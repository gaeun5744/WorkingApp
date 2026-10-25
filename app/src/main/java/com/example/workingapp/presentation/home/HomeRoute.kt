package com.example.workingapp.presentation.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.example.workingapp.service.StepCounterService

@Composable
fun HomeRoute(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> viewModel.onPermissionChecked(results[Manifest.permission.ACTIVITY_RECOGNITION] == true) }

    // 설정 화면에서 권한을 바꾸고 돌아오는 경우까지 반영하려고 화면이 다시 보일 때마다 확인한다
    LifecycleResumeEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChecked(granted)
        onPauseOrDispose { }
    }

    val isTracking = uiState is HomeUiState.Tracking
    LaunchedEffect(isTracking) {
        if (isTracking) StepCounterService.startIfPermitted(context)
    }

    HomeScreen(
        state = uiState,
        onEvent = { event ->
            when (event) {
                HomeEvent.RequestPermission -> permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS),
                )
                HomeEvent.OpenSettings -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
                is HomeEvent.Navigate -> onNavigate(event.key)
            }
        },
        modifier = modifier,
    )
}
