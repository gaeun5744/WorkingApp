package com.example.workingapp.presentation.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.domain.model.RankingPeriod
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.presentation.text.asString
import com.example.workingapp.ui.theme.WorkingAppTheme
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalTime

@Composable
fun RankingScreen(
    state: RankingUiState,
    onEvent: (RankingEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Header(content = state.content, onEvent = onEvent)
            PeriodTabs(selected = state.period, onSelected = { onEvent(RankingEvent.SelectPeriod(it)) })
            when (val content = state.content) {
                RankingContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is RankingContent.Error -> ErrorContent(content, onEvent)
                is RankingContent.Loaded -> LoadedContent(content, isRefreshing = state.isRefreshing, onEvent = onEvent)
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(content: RankingContent, onEvent: (RankingEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val title = (content as? RankingContent.Loaded)?.let { stringResource(R.string.ranking_title, it.regionName) }
            ?: stringResource(R.string.screen_ranking)
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onEvent(RankingEvent.ChangeRegion) }) { Text(stringResource(R.string.ranking_change_region)) }
    }
}

@Composable
private fun PeriodTabs(selected: RankingPeriod, onSelected: (RankingPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        RankingPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = RankingPeriod.entries.size),
            ) {
                Text(stringResource(if (period == RankingPeriod.DAILY) R.string.ranking_daily else R.string.ranking_weekly))
            }
        }
    }
}

@Composable
private fun ErrorContent(error: RankingContent.Error, onEvent: (RankingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(error.message.asString())
        if (error.regionNotSet) {
            Button(onClick = { onEvent(RankingEvent.ChangeRegion) }) { Text(stringResource(R.string.onboarding_select_region)) }
        } else {
            Button(onClick = { onEvent(RankingEvent.Retry) }) { Text(stringResource(R.string.retry)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedContent(content: RankingContent.Loaded, isRefreshing: Boolean, onEvent: (RankingEvent) -> Unit) {
    // 내 순위는 목록을 스크롤해도 보이도록 목록 밖에 고정한다
    MyRankCard(me = content.me)
    Text(
        text = stringResource(R.string.ranking_fetched_at, content.fetchedAt.hour, content.fetchedAt.minute),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { onEvent(RankingEvent.Refresh) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(content.entries, key = { it.userId }) { entry ->
                RankingRow(entry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MyRankCard(me: RankingEntryUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.ranking_my_rank, me.rank), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(text = stringResource(R.string.ranking_steps, me.steps), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RankingRow(entry: RankingEntryUi) {
    val background = if (entry.isMe) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.rank.toString(),
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (entry.rank <= TOP_HIGHLIGHT) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = if (entry.isMe) stringResource(R.string.ranking_me_suffix, entry.nickname) else entry.nickname,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = stringResource(R.string.ranking_steps, entry.steps))
    }
}

private const val TOP_HIGHLIGHT = 3

private fun previewContent(): RankingContent.Loaded {
    val entries = listOf(
        RankingEntryUi(1, "n_1", "산책왕", 15_200, isMe = false),
        RankingEntryUi(2, "u_me", "걷는토끼", 9_000, isMe = true),
        RankingEntryUi(3, "n_2", "아침러너", 8_100, isMe = false),
        RankingEntryUi(4, "n_3", "닉네임이아주긴이웃주민", 7_000, isMe = false),
    )
    return RankingContent.Loaded("역삼동", entries.toImmutableList(), entries[1], LocalTime.of(21, 30))
}

@Preview(showBackground = true)
@Composable
private fun RankingScreenPreview() {
    WorkingAppTheme { RankingScreen(state = RankingUiState(content = previewContent()), onEvent = {}) }
}

@Preview(showBackground = true)
@Composable
private fun RankingScreenRegionNotSetPreview() {
    WorkingAppTheme {
        RankingScreen(
            state = RankingUiState(content = RankingContent.Error(UiText.Resource(R.string.ranking_region_not_set), regionNotSet = true)),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RankingScreenLoadingPreview() {
    WorkingAppTheme { RankingScreen(state = RankingUiState(period = RankingPeriod.WEEKLY), onEvent = {}) }
}
