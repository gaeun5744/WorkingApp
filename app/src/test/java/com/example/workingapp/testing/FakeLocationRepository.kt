package com.example.workingapp.testing

import com.example.workingapp.domain.model.DeviceLocation
import com.example.workingapp.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart

class FakeLocationRepository : LocationRepository {

    val locations = MutableSharedFlow<DeviceLocation>(replay = 1)
    var subscriptions = 0
        private set

    override fun locationUpdates(): Flow<DeviceLocation> = locations.onStart { subscriptions++ }
}
