package com.example.workingapp.presentation.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingKey : NavKey

@Serializable
data object HomeKey : NavKey

@Serializable
data object HistoryKey : NavKey

@Serializable
data object RankingKey : NavKey

@Serializable
data object WalletKey : NavKey

@Serializable
data object RegionSelectKey : NavKey

/** 동네 선택 화면 → 호출한 화면으로 돌려주는 결과 (`ResultEventBus`) */
data class RegionResult(val id: Long, val name: String)
