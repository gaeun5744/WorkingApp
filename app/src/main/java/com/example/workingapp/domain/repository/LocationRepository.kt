package com.example.workingapp.domain.repository

import com.example.workingapp.domain.model.DeviceLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {

    /** 구독하는 동안만 위치를 받는다 (화면이 보일 때만 구독 → 백그라운드 위치 없음). 권한이 없으면 아무것도 내보내지 않는다. */
    fun locationUpdates(): Flow<DeviceLocation>
}
