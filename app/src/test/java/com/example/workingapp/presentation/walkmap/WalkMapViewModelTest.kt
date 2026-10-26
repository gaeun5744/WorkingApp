package com.example.workingapp.presentation.walkmap

import app.cash.turbine.test
import com.example.workingapp.R
import com.example.workingapp.domain.geo.offsetMeters
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.DeviceLocation
import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.domain.model.TreasureOpenResult
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.testing.FakeLocationRepository
import com.example.workingapp.testing.FakeTreasureRepository
import com.example.workingapp.testing.MainDispatcherRule
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WalkMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val treasures = FakeTreasureRepository()
    private val locations = FakeLocationRepository()
    // MainDispatcherRule 이 Main 을 바꾼 뒤에 만들어야 init 의 로드가 테스트 디스패처에서 돈다
    private val viewModel by lazy { WalkMapViewModel(treasures, locations) }

    private val t0 = FakeTreasureRepository.nearby().treasures[0].location

    private fun deviceAt(point: GeoPoint, accuracy: Float = 10f) =
        DeviceLocation(point, accuracy, isMock = false, measuredAt = Instant.parse("2026-10-26T12:00:00Z"))

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun state() = viewModel.uiState.value

    @Test
    fun `정확한 위치 권한으로 가장 가까운 상자 50m 안이면 열 수 있다`() = runTest {
        subscribe()
        viewModel.onPermissionResult(LocationPermission.PRECISE)
        locations.locations.emit(deviceAt(t0.offsetMeters(northMeters = 30.0, eastMeters = 0.0)))

        assertEquals("t0", state().nearest?.id)
        assertEquals(30, state().nearest?.distanceMeters)
        assertTrue(state().canOpenNearest)
    }

    @Test
    fun `80m 떨어져 있거나 정확도가 50m 보다 나쁘면 열 수 없다`() = runTest {
        subscribe()
        viewModel.onPermissionResult(LocationPermission.PRECISE)

        locations.locations.emit(deviceAt(t0.offsetMeters(northMeters = 80.0, eastMeters = 0.0)))
        assertFalse(state().canOpenNearest)

        locations.locations.emit(deviceAt(t0, accuracy = 70f))
        assertFalse(state().canOpenNearest)
    }

    @Test
    fun `표시 거리가 50m 로 반올림돼도 실제로 50m 를 넘으면 열 수 없다 (서버 판정과 일치)`() = runTest {
        subscribe()
        viewModel.onPermissionResult(LocationPermission.PRECISE)
        locations.locations.emit(deviceAt(t0.offsetMeters(northMeters = 50.4, eastMeters = 0.0)))

        assertEquals(50, state().nearest?.distanceMeters)
        assertFalse(state().canOpenNearest)
    }

    @Test
    fun `대략적인 위치만 허용했으면 가까워도 열 수 없다`() = runTest {
        subscribe()
        viewModel.onPermissionResult(LocationPermission.APPROXIMATE)
        locations.locations.emit(deviceAt(t0))

        assertFalse(state().canOpenNearest)
    }

    @Test
    fun `권한이 없으면 위치를 구독하지 않고 상자도 부르지 않는다`() = runTest {
        subscribe()
        viewModel.onPermissionResult(LocationPermission.DENIED)

        assertEquals(0, locations.subscriptions)
        assertEquals(null, state().myLocation)
        assertEquals(TreasureContent.WaitingForLocation, state().content)
        assertEquals(emptyList<GeoPoint>(), treasures.nearbyCalls)
    }

    @Test
    fun `같은 칸 안에서 움직이면 다시 부르지 않고, 다른 칸으로 넘어가면 그 주변을 부른다`() = runTest {
        subscribe()
        viewModel.onPermissionResult(LocationPermission.PRECISE)
        val origin = FakeTreasureRepository.ORIGIN

        locations.locations.emit(deviceAt(origin))
        locations.locations.emit(deviceAt(origin.offsetMeters(northMeters = 30.0, eastMeters = 30.0)))
        assertEquals(1, treasures.nearbyCalls.size)

        val nextCell = origin.offsetMeters(northMeters = 0.0, eastMeters = 600.0)
        locations.locations.emit(deviceAt(nextCell))
        assertEquals(listOf(origin, nextCell), treasures.nearbyCalls)
    }

    @Test
    fun `오늘 상한(5개)을 다 열었으면 가까워도 열 수 없다`() = runTest {
        treasures.nearbyResult = Result.success(FakeTreasureRepository.nearby(openedToday = 5))
        subscribe()
        viewModel.onPermissionResult(LocationPermission.PRECISE)
        locations.locations.emit(deviceAt(t0))

        assertTrue((state().content as TreasureContent.Loaded).reachedDailyLimit)
        assertFalse(state().canOpenNearest)
    }

    @Test
    fun `열면 현재 위치로 요청하고, 상자를 열림으로 바꾸고 적립을 알려준다`() = runTest {
        subscribe()
        viewModel.onPermissionResult(LocationPermission.PRECISE)
        val here = deviceAt(t0)
        locations.locations.emit(here)

        viewModel.effects.test {
            viewModel.onOpenNearest()
            assertEquals(WalkMapEffect.ShowMessage(UiText.Resource(R.string.treasure_opened, persistentListOf(2, 2))), awaitItem())
        }
        assertEquals(listOf("t0" to here), treasures.openCalls)
        val content = state().content as TreasureContent.Loaded
        assertTrue(content.treasures.first { it.id == "t0" }.opened)
        assertEquals(1, content.openedToday)
        assertEquals("다음 가까운 상자로 바뀐다", "t1", state().nearest?.id)
    }

    @Test
    fun `서버가 순간이동으로 거부하면 이유를 알려주고 상자는 닫힌 채로 둔다`() = runTest {
        subscribe()
        treasures.openHandler = { _, _ -> Result.failure(AppError.InvalidRequest("TREASURE_TOO_FAST")) }
        viewModel.onPermissionResult(LocationPermission.PRECISE)
        locations.locations.emit(deviceAt(t0))

        viewModel.effects.test {
            viewModel.onOpenNearest()
            assertEquals(WalkMapEffect.ShowMessage(UiText.Resource(R.string.treasure_error_too_fast)), awaitItem())
        }
        assertFalse((state().content as TreasureContent.Loaded).treasures.first { it.id == "t0" }.opened)
    }

    @Test
    fun `응답을 기다리는 동안 다시 눌러도 한 번만 요청한다`() = runTest {
        subscribe()
        val response = CompletableDeferred<Result<TreasureOpenResult>>()
        treasures.openHandler = { _, _ -> response.await() }
        viewModel.onPermissionResult(LocationPermission.PRECISE)
        locations.locations.emit(deviceAt(t0))

        viewModel.onOpenNearest()
        assertTrue(state().isOpening)
        viewModel.onOpenNearest()
        response.complete(Result.success(TreasureOpenResult("t0", 2, 2, alreadyOpened = false)))

        assertEquals(1, treasures.openCalls.size)
        assertFalse(state().isOpening)
    }
}
