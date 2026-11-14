package com.example.workingapp.presentation.walkmap.components

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.presentation.walkmap.TreasureUi
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import kotlinx.collections.immutable.ImmutableList

/**
 * 네이버 지도 MapView 를 Compose 에 붙인다.
 * MapView 는 Activity 생명주기를 직접 전달받아야 해서(onCreate~onDestroy) 화면의 Lifecycle 을 관찰해 그대로 넘긴다.
 * 오버레이(상자 마커·내 위치)는 상태가 바뀔 때 갱신한다.
 */
@Composable
fun NaverMapView(
    treasures: ImmutableList<TreasureUi>,
    myLocation: GeoPoint?,
    modifier: Modifier = Modifier,
) {
    // Preview 에서는 SDK 를 띄울 수 없어 자리표시자를 그린다
    if (LocalInspectionMode.current) {
        Box(modifier.background(ComposeColor.LightGray))
        return
    }
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    MapLifecycle(mapView)

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.uiSettings.isZoomControlEnabled = false
            naverMap = map
        }
    }

    val markers = remember { mutableMapOf<String, Marker>() }
    LaunchedEffect(naverMap, treasures) {
        val map = naverMap ?: return@LaunchedEffect
        val ids = treasures.map { it.id }.toSet()
        (markers.keys - ids).forEach { id -> markers.remove(id)?.map = null }
        treasures.forEach { treasure ->
            val marker = markers.getOrPut(treasure.id) { Marker() }
            marker.position = treasure.location.toLatLng()
            marker.iconTintColor = if (treasure.opened) Color.GRAY else Color.rgb(255, 111, 15)
            marker.alpha = if (treasure.opened) OPENED_ALPHA else 1f
            marker.captionText = "${treasure.points}P"
            marker.map = map
        }
    }

    var cameraMoved by remember { mutableStateOf(false) }
    LaunchedEffect(naverMap, myLocation) {
        val map = naverMap ?: return@LaunchedEffect
        val overlay = map.locationOverlay
        if (myLocation == null) {
            overlay.isVisible = false
            return@LaunchedEffect
        }
        overlay.position = myLocation.toLatLng()
        overlay.isVisible = true
        // 위치를 처음 알게 됐을 때만 내 위치로 옮긴다 (사용자가 옮긴 지도를 되돌리지 않도록)
        if (!cameraMoved) {
            map.moveCamera(CameraUpdate.scrollAndZoomTo(myLocation.toLatLng(), INITIAL_ZOOM))
            cameraMoved = true
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun MapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var created = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    mapView.onCreate(null)
                    created = true
                }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> Unit // onDispose 에서 한 번만 처리
                Lifecycle.Event.ON_ANY -> Unit
            }
        }
        // 이미 RESUMED 인 화면에 붙으면 관찰 시작과 함께 ON_CREATE~ON_RESUME 이 차례로 전달된다
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // 화면에서 빠질 때(뒤로가기 등) 현재 상태에서 거꾸로 내려가며 정리한다
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            if (created) mapView.onDestroy()
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

private const val INITIAL_ZOOM = 15.0
private const val OPENED_ALPHA = 0.5f
