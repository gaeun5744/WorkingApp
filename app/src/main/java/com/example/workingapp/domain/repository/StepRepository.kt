package com.example.workingapp.domain.repository

import com.example.workingapp.domain.model.DailySteps
import com.example.workingapp.domain.model.StepReading
import kotlinx.coroutines.flow.Flow

interface StepRepository {

    val isSensorAvailable: Boolean

    /** 센서 측정값 스트림. 측정 서비스가 구독하고 [record] 로 반영한다. */
    fun readings(): Flow<StepReading>

    /** 측정값 하나를 걸음 기록에 반영한다. 증분과 체크포인트를 한 트랜잭션으로 저장한다. */
    suspend fun record(reading: StepReading)

    /** 오늘(KST) 걸음. 자정이 지나면 새 날짜로 자동 전환된다. */
    fun observeTodaySteps(): Flow<DailySteps>

    /** 최근 7일 중 미동기화 날짜를 서버에 올린다 (PRD Q5: 서버는 날짜별 max 로 반영하므로 재전송해도 안전). */
    suspend fun syncPendingSteps(): Result<Unit>
}
