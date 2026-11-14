package com.example.workingapp.presentation.walkmap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workingapp.presentation.text.resolve

@Composable
fun WalkMapRoute(
    modifier: Modifier = Modifier,
    viewModel: WalkMapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onPermissionResult(context.locationPermission()) }

    // 설정에서 권한을 바꾸고 돌아오는 경우까지 반영한다
    LifecycleResumeEffect(Unit) {
        viewModel.onPermissionResult(context.locationPermission())
        onPauseOrDispose { }
    }

    // 처음 들어왔을 때 권한이 없으면 바로 요청한다 (지도 화면에 들어온 것 자체가 위치를 쓰겠다는 의도)
    LaunchedEffect(Unit) {
        if (context.locationPermission() == LocationPermission.DENIED) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WalkMapEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message.resolve(context))
            }
        }
    }

    WalkMapScreen(
        state = uiState,
        onEvent = { event ->
            when (event) {
                WalkMapEvent.RequestPermission -> permissionLauncher.launch(LOCATION_PERMISSIONS)
                WalkMapEvent.OpenSettings -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
                WalkMapEvent.OpenNearest -> viewModel.onOpenNearest()
                WalkMapEvent.Retry -> viewModel.onRetry()
            }
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun Context.locationPermission(): LocationPermission {
    fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermission.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationPermission.APPROXIMATE
        else -> LocationPermission.DENIED
    }
}
