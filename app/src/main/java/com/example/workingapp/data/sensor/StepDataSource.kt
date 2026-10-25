package com.example.workingapp.data.sensor

import com.example.workingapp.domain.model.StepReading
import kotlinx.coroutines.flow.Flow

/** 걸음 센서 추상화 (PRD Q8). 테스트·에뮬레이터에서는 다른 구현으로 교체한다. */
interface StepDataSource {

    val isAvailable: Boolean

    /** 구독하는 동안 측정값을 흘려보낸다. 센서가 없으면 아무것도 내보내지 않고 끝난다. */
    fun readings(): Flow<StepReading>
}
