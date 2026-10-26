package com.example.workingapp.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.workingapp.domain.model.DeviceLocation
import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.domain.repository.LocationRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * Fused Location Provider 로 위치를 받는다. 구독(지도 화면이 보이는 동안)할 때만 요청하고
 * 구독이 끝나면 즉시 해제해 백그라운드 위치를 쓰지 않는다 (PRD Q20).
 */
internal class FusedLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationRepository {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // hasPermission() 으로 확인한 뒤에만 요청한다
    override fun locationUpdates(): Flow<DeviceLocation> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toDeviceLocation()) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // 확인과 요청 사이에 권한이 회수된 경우
            Timber.w(e, "위치 권한이 없어 위치 요청을 중단함")
            close()
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun hasPermission(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private fun Location.toDeviceLocation() = DeviceLocation(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = accuracy,
        isMock = isMockLocation(),
        measuredAt = Instant.ofEpochMilli(time),
    )

    // minSdk 30 이라 API 31 의 isMock 이 없는 기기는 이전 API 를 쓴다
    @Suppress("DEPRECATION")
    private fun Location.isMockLocation(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 5_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 2_000L
    }
}
