package com.example.workingapp.presentation.region

import com.example.workingapp.domain.model.Region
import com.example.workingapp.presentation.text.UiText
import kotlinx.collections.immutable.ImmutableList

sealed interface RegionSelectUiState {
    data object Loading : RegionSelectUiState
    data class Success(val regions: ImmutableList<Region>) : RegionSelectUiState
    data class Error(val message: UiText) : RegionSelectUiState
}
