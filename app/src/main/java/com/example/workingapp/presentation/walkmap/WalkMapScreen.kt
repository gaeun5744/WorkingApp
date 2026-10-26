package com.example.workingapp.presentation.walkmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.domain.model.GeoPoint
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.presentation.text.asString
import com.example.workingapp.presentation.walkmap.components.NaverMapView
import com.example.workingapp.ui.theme.WorkingAppTheme
import kotlinx.collections.immutable.persistentListOf

@Composable
fun WalkMapScreen(
    state: WalkMapUiState,
    onEvent: (WalkMapEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val loaded = state.content as? TreasureContent.Loaded
    Box(modifier = modifier.fillMaxSize()) {
        NaverMapView(
            treasures = loaded?.treasures ?: persistentListOf(),
            myLocation = state.myLocation,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnackbarHost(hostState = snackbarHostState)
            BottomPanel(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun BottomPanel(state: WalkMapUiState, onEvent: (WalkMapEvent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.walk_map_title), style = MaterialTheme.typography.titleMedium)
            when (val content = state.content) {
                TreasureContent.WaitingForLocation -> WaitingForLocation(state = state, onEvent = onEvent)
                TreasureContent.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                is TreasureContent.Error -> {
                    Text(stringResource(R.string.walk_map_load_failed))
                    TextButton(onClick = { onEvent(WalkMapEvent.Retry) }) { Text(stringResource(R.string.retry)) }
                }
                is TreasureContent.Loaded -> LoadedPanel(state = state, content = content, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun WaitingForLocation(state: WalkMapUiState, onEvent: (WalkMapEvent) -> Unit) {
    when (state.permission) {
        LocationPermission.UNKNOWN -> Unit
        LocationPermission.DENIED -> PermissionRequired(onEvent)
        LocationPermission.APPROXIMATE, LocationPermission.PRECISE -> Text(stringResource(R.string.walk_map_locating))
    }
}

@Composable
private fun PermissionRequired(onEvent: (WalkMapEvent) -> Unit) {
    Text(stringResource(R.string.walk_map_permission_title), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.walk_map_permission_description), style = MaterialTheme.typography.bodySmall)
    Button(onClick = { onEvent(WalkMapEvent.RequestPermission) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.walk_map_permission_request))
    }
    TextButton(onClick = { onEvent(WalkMapEvent.OpenSettings) }) { Text(stringResource(R.string.walk_map_open_settings)) }
}

@Composable
private fun LoadedPanel(state: WalkMapUiState, content: TreasureContent.Loaded, onEvent: (WalkMapEvent) -> Unit) {
    Text(
        text = stringResource(R.string.walk_map_progress, content.openedToday, content.dailyOpenLimit),
        style = MaterialTheme.typography.bodyMedium,
    )
    when (state.permission) {
        LocationPermission.UNKNOWN -> Unit
        LocationPermission.DENIED -> {
            Text(stringResource(R.string.walk_map_permission_title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.walk_map_permission_description), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onEvent(WalkMapEvent.RequestPermission) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.walk_map_permission_request))
            }
            TextButton(onClick = { onEvent(WalkMapEvent.OpenSettings) }) { Text(stringResource(R.string.walk_map_open_settings)) }
        }
        LocationPermission.APPROXIMATE -> {
            Text(stringResource(R.string.walk_map_precise_required), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { onEvent(WalkMapEvent.OpenSettings) }) { Text(stringResource(R.string.walk_map_open_settings)) }
        }
        LocationPermission.PRECISE -> NearestTreasure(state = state, content = content, onEvent = onEvent)
    }
}

@Composable
private fun NearestTreasure(state: WalkMapUiState, content: TreasureContent.Loaded, onEvent: (WalkMapEvent) -> Unit) {
    val nearest = state.nearest
    when {
        content.reachedDailyLimit -> Text(stringResource(R.string.walk_map_all_opened))
        state.myLocation == null || nearest?.distanceMeters == null -> Text(stringResource(R.string.walk_map_locating))
        else -> {
            Text(stringResource(R.string.walk_map_nearest, nearest.distanceMeters), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { onEvent(WalkMapEvent.OpenNearest) },
                enabled = state.canOpenNearest && !state.isOpening,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isOpening) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.walk_map_open, nearest.points))
                }
            }
            if (!state.canOpenNearest) {
                Text(
                    text = stringResource(R.string.walk_map_open_hint, content.openRadiusMeters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val PREVIEW_CENTER = GeoPoint(37.5446, 127.0560)

private fun previewContent(openedCount: Int = 1) = TreasureContent.Loaded(
    openRadiusMeters = 50,
    dailyOpenLimit = 5,
    openedToday = openedCount,
    treasures = persistentListOf(
        TreasureUi("t0", PREVIEW_CENTER, 2, opened = openedCount >= 1, distanceMeters = 320),
        TreasureUi("t1", PREVIEW_CENTER, 2, opened = openedCount >= 2, distanceMeters = 35),
        TreasureUi("t2", PREVIEW_CENTER, 2, opened = false, distanceMeters = 640),
    ),
)

@Preview(showBackground = true)
@Composable
private fun WalkMapNearPreview() {
    val content = previewContent()
    WorkingAppTheme {
        WalkMapScreen(
            state = WalkMapUiState(
                permission = LocationPermission.PRECISE,
                content = content,
                myLocation = PREVIEW_CENTER,
                nearest = content.treasures[1],
                canOpenNearest = true,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkMapFarPreview() {
    val content = previewContent()
    WorkingAppTheme {
        WalkMapScreen(
            state = WalkMapUiState(
                permission = LocationPermission.PRECISE,
                content = content,
                myLocation = PREVIEW_CENTER,
                nearest = content.treasures[0].copy(opened = false),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkMapPermissionPreview() {
    WorkingAppTheme {
        WalkMapScreen(state = WalkMapUiState(permission = LocationPermission.DENIED), onEvent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkMapErrorPreview() {
    WorkingAppTheme {
        WalkMapScreen(
            state = WalkMapUiState(content = TreasureContent.Error(UiText.Resource(R.string.error_network))),
            onEvent = {},
        )
    }
}
