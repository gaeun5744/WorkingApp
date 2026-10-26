package com.example.workingapp.presentation.wallet

import com.example.workingapp.R
import com.example.workingapp.domain.model.AppError
import com.example.workingapp.domain.model.Wallet
import com.example.workingapp.domain.model.WalletTransaction
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.testing.FakeRewardRepository
import com.example.workingapp.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.OffsetDateTime

class WalletViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRewardRepository()

    @Test
    fun `잔액과 적립 내역을 보여준다`() {
        val transaction = WalletTransaction("t_1", 5, rewardTier = 3, createdAt = OffsetDateTime.parse("2026-10-25T21:30:00+09:00"))
        repository.walletResult = Result.success(Wallet(balance = 5, transactions = listOf(transaction)))

        val state = WalletViewModel(repository).uiState.value as WalletUiState.Content

        assertEquals(5, state.balance)
        assertEquals(listOf(transaction), state.transactions)
    }

    @Test
    fun `실패하면 오류를 보여주고 재시도하면 다시 불러온다`() {
        repository.walletResult = Result.failure(AppError.Network)
        val viewModel = WalletViewModel(repository)
        assertEquals(WalletUiState.Error(UiText.Resource(R.string.error_network)), viewModel.uiState.value)

        repository.walletResult = Result.success(Wallet(balance = 0, transactions = emptyList()))
        viewModel.onRetry()

        assertEquals(2, repository.walletCalls)
        assertEquals(0, (viewModel.uiState.value as WalletUiState.Content).balance)
    }
}
