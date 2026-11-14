package com.example.workingapp.data.remote.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workingapp.data.local.SessionStore
import com.example.workingapp.data.local.WorkingAppDatabase
import com.example.workingapp.data.local.entity.DailyStepsEntity
import com.example.workingapp.data.repository.AccountRepositoryImpl
import com.example.workingapp.domain.model.Region
import com.example.workingapp.testing.MockBackend
import com.example.workingapp.testing.MutableClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.OffsetDateTime

/** 실제 OkHttp 경로(AuthInterceptor → Mock)로 토큰 갱신과 자동 재가입을 검증한다. */
@RunWith(AndroidJUnit4::class)
class AuthRenewalTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(OffsetDateTime.parse("2026-10-25T10:00:00+09:00").toInstant())
    private lateinit var database: WorkingAppDatabase
    private lateinit var sessionStore: SessionStore

    @Before
    fun setUp() {
        context.getSharedPreferences(SessionStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, WorkingAppDatabase::class.java).build()
        sessionStore = SessionStore(context)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun onboard(backend: MockBackend) {
        val account = AccountRepositoryImpl(backend.api, sessionStore, backend.json)
        account.signUp("걷는토끼").getOrThrow()
        account.updateRegion(Region(1, "역삼동")).getOrThrow()
    }

    @Test
    fun 액세스_토큰이_만료되면_재발급받아_요청을_이어간다() = runTest {
        val backend = MockBackend(clock, sessionStore, database.stepDao())
        onboard(backend)
        val oldToken = sessionStore.accessToken

        clock.advance(Duration.ofMinutes(11))
        val me = backend.api.getMe()

        assertEquals("걷는토끼", me.nickname)
        assertNotEquals(oldToken, sessionStore.accessToken)
    }

    @Test
    fun 서버가_계정을_잃으면_같은_닉네임과_동네로_재가입하고_걸음을_다시_올리도록_표시한다() = runTest {
        val before = MockBackend(clock, sessionStore, database.stepDao())
        onboard(before)
        val oldUserId = before.api.getMe().userId
        database.stepDao().upsertDaily(DailyStepsEntity("2026-10-25", 3_000, updatedAtMillis = 1, synced = true, goal = 10_000))

        // Mock 프로세스 재시작: 서버 상태가 비어 저장된 토큰이 모두 무효가 된다
        val after = MockBackend(clock, sessionStore, database.stepDao())
        val me = after.api.getMe()

        assertNotEquals(oldUserId, me.userId)
        assertEquals("걷는토끼", me.nickname)
        assertEquals(1L, me.region?.id)
        assertEquals(false, database.stepDao().getDaily("2026-10-25")?.synced)
    }
}
