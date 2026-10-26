package com.example.workingapp.presentation.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.domain.model.WalletReason
import com.example.workingapp.domain.model.WalletTransaction
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.presentation.text.asString
import com.example.workingapp.ui.theme.WorkingAppTheme
import kotlinx.collections.immutable.persistentListOf
import java.time.OffsetDateTime

@Composable
fun WalletScreen(
    state: WalletUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.screen_wallet), style = MaterialTheme.typography.titleLarge)
        when (state) {
            WalletUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is WalletUiState.Error -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.message.asString())
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            is WalletUiState.Content -> {
                BalanceCard(balance = state.balance)
                Text(text = stringResource(R.string.wallet_history), style = MaterialTheme.typography.titleMedium)
                if (state.transactions.isEmpty()) {
                    Text(text = stringResource(R.string.wallet_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn {
                        items(state.transactions, key = { it.id }) { transaction ->
                            TransactionRow(transaction)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = stringResource(R.string.wallet_balance_label), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.wallet_balance, balance), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun TransactionRow(transaction: WalletTransaction) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            val reason = when (val walletReason = transaction.reason) {
                is WalletReason.RewardTier -> stringResource(R.string.wallet_reason_reward_tier, TIER_STEPS[walletReason.tier] ?: 0)
                WalletReason.Treasure -> stringResource(R.string.wallet_reason_treasure)
                WalletReason.Other -> stringResource(R.string.wallet_reason_other)
            }
            Text(text = reason, style = MaterialTheme.typography.bodyLarge)
            val time = transaction.createdAt
            Text(
                text = stringResource(R.string.wallet_date_time, time.monthValue, time.dayOfMonth, time.hour, time.minute),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.wallet_amount, transaction.amount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// 적립 내역에는 구간 번호만 오므로 화면 표시용 걸음 수를 둔다 (docs/api.md 리워드 구간 표와 같게 유지)
private val TIER_STEPS = mapOf(1 to 1_000, 2 to 3_000, 3 to 5_000, 4 to 7_000, 5 to 10_000)

@Preview(showBackground = true)
@Composable
private fun WalletScreenContentPreview() {
    WorkingAppTheme {
        WalletScreen(
            state = WalletUiState.Content(
                balance = 9,
                transactions = persistentListOf(
                    WalletTransaction("t_3", 2, WalletReason.Treasure, createdAt = OffsetDateTime.parse("2026-10-26T21:05:00+09:00")),
                    WalletTransaction("t_2", 5, WalletReason.RewardTier(3), createdAt = OffsetDateTime.parse("2026-10-25T21:30:00+09:00")),
                    WalletTransaction("t_1", 1, WalletReason.RewardTier(1), createdAt = OffsetDateTime.parse("2026-10-25T09:05:00+09:00")),
                ),
            ),
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WalletScreenEmptyPreview() {
    WorkingAppTheme { WalletScreen(state = WalletUiState.Content(0, persistentListOf()), onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun WalletScreenErrorPreview() {
    WorkingAppTheme { WalletScreen(state = WalletUiState.Error(UiText.Resource(R.string.error_network)), onRetry = {}) }
}
