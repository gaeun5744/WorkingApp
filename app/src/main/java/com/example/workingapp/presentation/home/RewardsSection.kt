package com.example.workingapp.presentation.home

import kotlinx.collections.immutable.ImmutableList

sealed interface RewardsSection {
    data object Loading : RewardsSection
    data object Error : RewardsSection
    data class Loaded(val tiers: ImmutableList<RewardTierUi>) : RewardsSection
}
