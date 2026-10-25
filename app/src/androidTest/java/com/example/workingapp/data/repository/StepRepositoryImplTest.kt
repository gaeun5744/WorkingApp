package com.example.workingapp.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workingapp.data.local.WorkingAppDatabase
import com.example.workingapp.data.sensor.StepDataSource
import com.example.workingapp.domain.model.StepReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/** 실제 Room(in-memory)으로 증분·체크포인트가 한 트랜잭션으로 저장되는지 검증한다. */
@RunWith(AndroidJUnit4::class)
class StepRepositoryImplTest {

    private val now: Instant = OffsetDateTime.parse("2026-10-25T10:30:00+09:00").toInstant()
    private val clock = Clock.fixed(now, ZoneId.of("Asia/Seoul"))
    private lateinit var database: WorkingAppDatabase
    private lateinit var repository: StepRepositoryImpl

    private val noSensor = object : StepDataSource {
        override val isAvailable = true
        override fun readings(): Flow<StepReading> = emptyFlow()
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkingAppDatabase::class.java).build()
        repository = StepRepositoryImpl(database, noSensor, clock)
    }

    @After
    fun tearDown() = database.close()

    private fun reading(raw: Long, boot: Int = 1) = StepReading(raw, boot, now)

    @Test
    fun 첫_측정은_기준만_저장하고_이후_증분을_날짜와_시간대에_더한다() = runTest {
        repository.record(reading(5_000))
        repository.record(reading(5_230))
        repository.record(reading(5_300))

        assertEquals(300, repository.observeTodaySteps().first().steps)
        val hourly = database.stepDao().getHourlyOf("2026-10-25")
        assertEquals(listOf(10 to 300), hourly.map { it.hour to it.steps })
        assertEquals(false, database.stepDao().getDaily("2026-10-25")?.synced)
    }

    @Test
    fun 재부팅_후에는_현재_값_전체를_더한다() = runTest {
        repository.record(reading(5_000, boot = 1))
        repository.record(reading(5_100, boot = 1))
        repository.record(reading(40, boot = 2))

        assertEquals(140, repository.observeTodaySteps().first().steps)
    }

    @Test
    fun 같은_측정값이_동시에_여러_번_기록돼도_한_번만_센다() = runTest {
        repository.record(reading(1_000))

        // 서비스와 워커가 같은 값을 동시에 기록하는 상황을 과장해서 재현
        (1..20).map { async(Dispatchers.IO) { repository.record(reading(1_500)) } }.awaitAll()

        assertEquals(500, repository.observeTodaySteps().first().steps)
    }

    @Test
    fun 기록이_없는_날은_0걸음이다() = runTest {
        assertEquals(0, repository.observeTodaySteps().first().steps)
    }
}
