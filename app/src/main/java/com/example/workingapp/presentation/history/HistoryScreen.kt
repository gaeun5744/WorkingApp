package com.example.workingapp.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.domain.history.HistoryPeriod
import com.example.workingapp.domain.history.HistoryRange
import com.example.workingapp.domain.history.HistorySummary
import com.example.workingapp.presentation.history.components.StepBarChart
import com.example.workingapp.ui.theme.WorkingAppTheme
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onPeriodSelected: (HistoryPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.screen_history), style = MaterialTheme.typography.titleLarge)
        when (state) {
            HistoryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is HistoryUiState.Content -> {
                PeriodTabs(selected = state.period, onSelected = onPeriodSelected)
                PeriodNavigator(state = state, onPrevious = onPrevious, onNext = onNext)
                SummaryRow(period = state.period, summary = state.summary)
                StepBarChart(bars = state.bars, labelOf = barLabel(state.period, state.range))
            }
        }
    }
}

@Composable
private fun PeriodTabs(selected: HistoryPeriod, onSelected: (HistoryPeriod) -> Unit) {
    val labels = stringArrayResource(R.array.history_periods)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        HistoryPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = HistoryPeriod.entries.size),
            ) { Text(labels[index]) }
        }
    }
}

@Composable
private fun PeriodNavigator(state: HistoryUiState.Content, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious) { Text(stringResource(R.string.history_previous)) }
        Text(
            text = rangeTitle(state.period, state.range),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onNext, enabled = state.canGoNext) { Text(stringResource(R.string.history_next)) }
    }
}

@Composable
private fun rangeTitle(period: HistoryPeriod, range: HistoryRange): String = when (period) {
    HistoryPeriod.DAY -> stringResource(R.string.history_date, range.start.monthValue, range.start.dayOfMonth)
    HistoryPeriod.WEEK -> stringResource(
        R.string.history_week_range,
        range.start.monthValue,
        range.start.dayOfMonth,
        range.end.monthValue,
        range.end.dayOfMonth,
    )
    HistoryPeriod.MONTH -> stringResource(R.string.history_month, range.start.year, range.start.monthValue)
}

@Composable
private fun SummaryRow(period: HistoryPeriod, summary: HistorySummary) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SummaryItem(stringResource(R.string.history_total), stringResource(R.string.history_steps_value, summary.totalSteps))
        if (period == HistoryPeriod.DAY) {
            val achieved = if (summary.achievedDays > 0) R.string.history_goal_achieved else R.string.history_goal_not_achieved
            SummaryItem(stringResource(R.string.history_goal), stringResource(achieved))
        } else {
            SummaryItem(stringResource(R.string.history_average), stringResource(R.string.history_steps_value, summary.averageSteps))
            SummaryItem(stringResource(R.string.history_goal), stringResource(R.string.history_achieved_days, summary.achievedDays))
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 막대 라벨: 일간은 6시간마다, 주간은 요일, 월간은 1일·5일 간격 */
@Composable
private fun barLabel(period: HistoryPeriod, range: HistoryRange): (Int) -> String? {
    val weekdays = stringArrayResource(R.array.history_weekdays)
    return { index ->
        when (period) {
            HistoryPeriod.DAY -> if (index % 6 == 0) index.toString() else null
            HistoryPeriod.WEEK -> weekdays[index]
            HistoryPeriod.MONTH -> (index + 1).takeIf { it == 1 || it % 5 == 0 }?.toString()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenWeekPreview() {
    val monday = LocalDate.of(2026, 10, 19)
    WorkingAppTheme {
        HistoryScreen(
            state = HistoryUiState.Content(
                period = HistoryPeriod.WEEK,
                range = HistoryRange.of(HistoryPeriod.WEEK, monday),
                bars = listOf(4_000, 11_000, 0, 8_000, 12_500, 6_000, 2_000)
                    .map { ChartBar(it, achieved = it >= 10_000) }.toImmutableList(),
                summary = HistorySummary(totalSteps = 43_500, averageSteps = 6_214, achievedDays = 2),
                canGoNext = false,
            ),
            onPeriodSelected = {},
            onPrevious = {},
            onNext = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenDayPreview() {
    val day = LocalDate.of(2026, 10, 25)
    WorkingAppTheme {
        HistoryScreen(
            state = HistoryUiState.Content(
                period = HistoryPeriod.DAY,
                range = HistoryRange.of(HistoryPeriod.DAY, day),
                bars = (0 until 24).map { ChartBar(if (it in 8..21) it * 120 else 0, achieved = false) }.toImmutableList(),
                summary = HistorySummary(totalSteps = 5_230, averageSteps = 5_230, achievedDays = 0),
                canGoNext = false,
            ),
            onPeriodSelected = {},
            onPrevious = {},
            onNext = {},
        )
    }
}
