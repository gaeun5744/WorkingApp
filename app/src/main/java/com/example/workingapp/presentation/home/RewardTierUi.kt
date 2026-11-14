package com.example.workingapp.presentation.home

data class RewardTierUi(
    val tier: Int,
    val requiredSteps: Int,
    val points: Int,
    val state: RewardTierState,
)
