package com.example.workingapp.presentation.walkmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workingapp.R
import com.example.workingapp.domain.geo.GeoCell
import com.example.workingapp.domain.geo.distanceMetersTo
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.DeviceLocation
import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.domain.model.NearbyTreasures
import com.example.workingapp.domain.model.TreasureOpenResult
import com.example.workingapp.domain.repository.LocationRepository
import com.example.workingapp.domain.repository.TreasureRepository
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.presentation.text.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class WalkMapViewModel @Inject constructor(
    private val treasureRepository: TreasureRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val permission = MutableStateFlow(LocationPermission.UNKNOWN)
    private val treasures = MutableStateFlow<TreasuresLoad>(TreasuresLoad.WaitingForLocation)
    private val openingId = MutableStateFlow<String?>(null)
    private var lastLocation: DeviceLocation? = null
    private var lastFetchedCell: GeoCell? = null
    private var fetchJob: Job? = null

    private val _effects = Channel<WalkMapEffect>(Channel.BUFFERED)
    val effects: Flow<WalkMapEffect> = _effects.receiveAsFlow()

    // 권한이 있을 때만 위치를 구독한다. uiState 가 WhileSubscribed 라 화면이 사라지면 위치 요청도 함께 멈춘다.
    // 다른 격자 칸으로 들어서면 그 주변 상자를 다시 불러온다 (같은 칸 안의 이동은 다시 부르지 않음)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val locations: Flow<DeviceLocation?> = permission
        .flatMapLatest { permission ->
            if (permission == LocationPermission.PRECISE || permission == LocationPermission.APPROXIMATE) {
                locationRepository.locationUpdates().map<DeviceLocation, DeviceLocation?> { it }.onStart { emit(null) }
            } else {
                flowOf(null)
            }
        }
        .onEach { location ->
            lastLocation = location
            val cell = location?.let { GeoCell.of(it.point) } ?: return@onEach
            if (cell != lastFetchedCell) {
                lastFetchedCell = cell
                fetch(around = location.point)
            }
        }

    val uiState: StateFlow<WalkMapUiState> =
        combine(permission, treasures, locations, openingId) { permission, load, location, openingId ->
            buildState(permission, load, location, openingId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalkMapUiState())

    fun onPermissionResult(result: LocationPermission) {
        permission.value = result
    }

    fun onRetry() {
        lastLocation?.let { fetch(around = it.point) }
    }

    /**
     * 가장 가까운 닫힌 상자를 연다. 위치 검증에서 거절될 수 있는 요청이라 낙관적으로 반영하지 않고
     * 서버 응답을 받은 뒤 상태를 바꾼다 (M5 리워드와 다른 점).
     */
    fun onOpenNearest() {
        if (openingId.value != null) return
        val state = uiState.value
        val target = state.nearest ?: return
        val location = lastLocation ?: return
        if (!state.canOpenNearest) return
        openingId.value = target.id

        viewModelScope.launch {
            treasureRepository.open(target.id, location)
                .onSuccess { result ->
                    markOpened(result)
                    _effects.send(WalkMapEffect.ShowMessage(result.message()))
                }
                .onFailure { error ->
                    _effects.send(WalkMapEffect.ShowMessage(error.toOpenErrorText()))
                    if ((error as? AppError.InvalidRequest)?.code == "TREASURE_NOT_TODAY") fetch(around = location.point)
                }
            openingId.value = null
        }
    }

    // 새 칸의 요청이 시작되면 이전 칸의 응답은 버린다 (늦게 도착한 응답이 최신 결과를 덮지 않도록)
    private fun fetch(around: GeoPoint) {
        fetchJob?.cancel()
        if (treasures.value !is TreasuresLoad.Loaded) treasures.value = TreasuresLoad.Loading
        fetchJob = viewModelScope.launch {
            treasureRepository.getNearbyTreasures(around)
                .onSuccess { treasures.value = TreasuresLoad.Loaded(it) }
                .onFailure { error ->
                    if (treasures.value !is TreasuresLoad.Loaded) treasures.value = TreasuresLoad.Error(error.toUiText())
                }
        }
    }

    private fun markOpened(result: TreasureOpenResult) = treasures.update { load ->
        if (load !is TreasuresLoad.Loaded) return@update load
        val nearby = load.nearby
        val wasClosed = nearby.treasures.any { it.id == result.treasureId && !it.opened }
        TreasuresLoad.Loaded(
            nearby.copy(
                treasures = nearby.treasures.map { if (it.id == result.treasureId) it.copy(opened = true) else it },
                openedToday = if (wasClosed && !result.alreadyOpened) nearby.openedToday + 1 else nearby.openedToday,
            ),
        )
    }

    private fun buildState(
        permission: LocationPermission,
        load: TreasuresLoad,
        location: DeviceLocation?,
        openingId: String?,
    ): WalkMapUiState {
        val content = when (load) {
            TreasuresLoad.WaitingForLocation -> TreasureContent.WaitingForLocation
            TreasuresLoad.Loading -> TreasureContent.Loading
            is TreasuresLoad.Error -> TreasureContent.Error(load.message)
            is TreasuresLoad.Loaded -> load.nearby.toContent(location)
        }
        val loaded = content as? TreasureContent.Loaded
        val nearest = loaded?.treasures
            ?.filter { !it.opened && it.distanceMeters != null }
            ?.minByOrNull { it.distanceMeters ?: Int.MAX_VALUE }
        // 표시용 거리는 반올림한 값이라, 판정은 서버와 같은 원래 거리로 한다 (50.4m 를 50m 로 보고 열기를 켜지 않도록)
        val exactDistance = if (nearest != null && location != null) location.point.distanceMetersTo(nearest.location) else null
        val canOpen = permission == LocationPermission.PRECISE &&
            exactDistance != null && loaded != null &&
            !loaded.reachedDailyLimit &&
            exactDistance <= loaded.openRadiusMeters &&
            (location?.accuracyMeters ?: Float.MAX_VALUE) <= MAX_ACCURACY_METERS
        return WalkMapUiState(
            permission = permission,
            content = content,
            myLocation = location?.point,
            accuracyMeters = location?.accuracyMeters,
            nearest = nearest,
            canOpenNearest = canOpen,
            isOpening = openingId != null,
        )
    }

    private fun NearbyTreasures.toContent(location: DeviceLocation?) = TreasureContent.Loaded(
        openRadiusMeters = openRadiusMeters,
        dailyOpenLimit = dailyOpenLimit,
        openedToday = openedToday,
        treasures = treasures.map { treasure ->
            TreasureUi(
                id = treasure.id,
                location = treasure.location,
                points = treasure.points,
                opened = treasure.opened,
                distanceMeters = location?.point?.distanceMetersTo(treasure.location)?.roundToInt(),
            )
        }.toImmutableList(),
    )

    private fun TreasureOpenResult.message(): UiText =
        if (alreadyOpened) {
            UiText.Resource(R.string.treasure_already_opened)
        } else {
            UiText.Resource(R.string.treasure_opened, persistentListOf(points, balance))
        }

    private fun Throwable.toOpenErrorText(): UiText = when ((this as? AppError.InvalidRequest)?.code) {
        "TREASURE_TOO_FAR" -> UiText.Resource(R.string.treasure_error_too_far)
        "TREASURE_LOW_ACCURACY" -> UiText.Resource(R.string.treasure_error_low_accuracy)
        "TREASURE_MOCK_LOCATION" -> UiText.Resource(R.string.treasure_error_mock_location)
        "TREASURE_TOO_FAST" -> UiText.Resource(R.string.treasure_error_too_fast)
        "TREASURE_NOT_TODAY" -> UiText.Resource(R.string.treasure_error_not_today)
        "TREASURE_DAILY_LIMIT" -> UiText.Resource(R.string.treasure_error_daily_limit)
        else -> toUiText()
    }

    private sealed interface TreasuresLoad {
        data object WaitingForLocation : TreasuresLoad
        data object Loading : TreasuresLoad
        data class Error(val message: UiText) : TreasuresLoad
        data class Loaded(val nearby: NearbyTreasures) : TreasuresLoad
    }

    private companion object {
        // 서버 검증(docs/api.md)과 같은 기준. 앱은 버튼 활성화에만 쓰고 최종 판정은 서버가 한다
        const val MAX_ACCURACY_METERS = 50f
    }
}
