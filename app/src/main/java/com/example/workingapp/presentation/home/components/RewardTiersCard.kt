package com.example.workingapp.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.presentation.home.RewardTierState
import com.example.workingapp.presentation.home.RewardTierUi
import com.example.workingapp.presentation.home.RewardsSection
import com.example.workingapp.ui.theme.WorkingAppTheme
import kotlinx.collections.immutable.persistentListOf

/** 구간별 리워드 버튼: 잠김(비활성) / 받기 / 받음(비활성) */
@Composable
fun RewardTiersCard(
    section: RewardsSection,
    onClaim: (tier: Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.reward_title), style = MaterialTheme.typography.titleMedium)
            when (section) {
                RewardsSection.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                RewardsSection.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.reward_load_failed), modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                is RewardsSection.Loaded -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    section.tiers.forEach { tier ->
                        TierButton(tier = tier, onClaim = onClaim, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TierButton(tier: RewardTierUi, onClaim: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(R.string.reward_tier_steps, tier.requiredSteps), style = MaterialTheme.typography.labelSmall)
        val label = when (tier.state) {
            RewardTierState.CLAIMED -> stringResource(R.string.reward_tier_claimed)
            else -> stringResource(R.string.reward_tier_points, tier.points)
        }
        val contentPadding = PaddingValues(horizontal = 4.dp)
        if (tier.state == RewardTierState.CLAIMABLE) {
            Button(onClick = { onClaim(tier.tier) }, contentPadding = contentPadding) { Text(label, maxLines = 1) }
        } else {
            OutlinedButton(onClick = {}, enabled = false, contentPadding = contentPadding) { Text(label, maxLines = 1) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RewardTiersCardPreview() {
    WorkingAppTheme {
        RewardTiersCard(
            section = RewardsSection.Loaded(
                persistentListOf(
                    RewardTierUi(1, 1_000, 1, RewardTierState.CLAIMED),
                    RewardTierUi(2, 3_000, 3, RewardTierState.CLAIMABLE),
                    RewardTierUi(3, 5_000, 5, RewardTierState.CLAIMABLE),
                    RewardTierUi(4, 7_000, 7, RewardTierState.LOCKED),
                    RewardTierUi(5, 10_000, 10, RewardTierState.LOCKED),
                ),
            ),
            onClaim = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RewardTiersCardErrorPreview() {
    WorkingAppTheme { RewardTiersCard(section = RewardsSection.Error, onClaim = {}, onRetry = {}) }
}
