package com.example.workingapp.presentation.wallet

import com.example.workingapp.domain.model.WalletTransaction
import com.example.workingapp.presentation.text.UiText
import kotlinx.collections.immutable.ImmutableList

sealed interface WalletUiState {
    data object Loading : WalletUiState
    data class Error(val message: UiText) : WalletUiState
    data class Content(
        val balance: Int,
        val transactions: ImmutableList<WalletTransaction>,
    ) : WalletUiState
}
