package com.example.workingapp.presentation.region

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.domain.model.Region
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.presentation.text.asString
import com.example.workingapp.ui.theme.WorkingAppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun RegionSelectScreen(
    query: String,
    state: RegionSelectUiState,
    onQueryChange: (String) -> Unit,
    onRegionClick: (Region) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.screen_region_select),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.region_search_placeholder)) },
            singleLine = true,
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                RegionSelectUiState.Loading -> CircularProgressIndicator()
                is RegionSelectUiState.Error -> ErrorContent(state.message, onRetry)
                is RegionSelectUiState.Success ->
                    if (state.regions.isEmpty()) {
                        Text(stringResource(R.string.region_empty))
                    } else {
                        RegionList(state.regions, onRegionClick)
                    }
            }
        }
    }
}

@Composable
private fun RegionList(regions: ImmutableList<Region>, onRegionClick: (Region) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(regions, key = { it.id }) { region ->
            Text(
                text = region.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRegionClick(region) }
                    .padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ErrorContent(message: UiText, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message.asString())
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionSelectContentPreview() {
    WorkingAppTheme {
        RegionSelectScreen(
            query = "동",
            state = RegionSelectUiState.Success(persistentListOf(Region(1, "역삼동"), Region(2, "삼성동"))),
            onQueryChange = {},
            onRegionClick = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionSelectEmptyPreview() {
    WorkingAppTheme {
        RegionSelectScreen("없는동", RegionSelectUiState.Success(persistentListOf()), {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun RegionSelectErrorPreview() {
    WorkingAppTheme {
        RegionSelectScreen("", RegionSelectUiState.Error(UiText.Resource(R.string.error_network)), {}, {}, {})
    }
}
