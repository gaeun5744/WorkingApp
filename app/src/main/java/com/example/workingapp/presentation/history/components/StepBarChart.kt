package com.example.workingapp.presentation.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.presentation.history.ChartBar
import com.example.workingapp.ui.theme.WorkingAppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Compose Canvas 로 직접 그리는 막대 그래프.
 * 목표를 달성한 막대는 primary, 아닌 막대는 연한 색으로 그린다. [labelOf] 가 null 을 주는 칸은 라벨을 비운다.
 */
@Composable
fun StepBarChart(
    bars: ImmutableList<ChartBar>,
    labelOf: (index: Int) -> String?,
    modifier: Modifier = Modifier,
) {
    val achievedColor = MaterialTheme.colorScheme.primary
    val normalColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val maxSteps = bars.maxOfOrNull { it.steps }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val slot = size.width / bars.size
            val barWidth = slot * BAR_WIDTH_RATIO
            bars.forEachIndexed { index, bar ->
                val barHeight = size.height * bar.steps / maxSteps
                if (barHeight <= 0f) return@forEachIndexed
                drawRoundRect(
                    color = if (bar.achieved) achievedColor else normalColor,
                    topLeft = Offset(x = slot * index + (slot - barWidth) / 2, y = size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.indices.forEach { index ->
                Text(
                    text = labelOf(index).orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private const val BAR_WIDTH_RATIO = 0.6f

@Preview(showBackground = true)
@Composable
private fun StepBarChartPreview() {
    WorkingAppTheme {
        StepBarChart(
            bars = listOf(4_000, 11_000, 0, 8_000, 12_500, 6_000, 2_000)
                .map { ChartBar(it, achieved = it >= 10_000) }
                .toImmutableList(),
            labelOf = { listOf("월", "화", "수", "목", "금", "토", "일")[it] },
        )
    }
}
