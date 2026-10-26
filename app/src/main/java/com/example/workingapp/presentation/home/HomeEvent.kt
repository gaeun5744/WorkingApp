package com.example.workingapp.presentation.home

import androidx.navigation3.runtime.NavKey

sealed interface HomeEvent {
    data object RequestPermission : HomeEvent
    data object OpenSettings : HomeEvent
    data class ChangeGoal(val goal: Int) : HomeEvent
    data class Navigate(val key: NavKey) : HomeEvent
}
