package com.example.workingapp.presentation.walkmap

sealed interface WalkMapEvent {
    data object RequestPermission : WalkMapEvent
    data object OpenSettings : WalkMapEvent
    data object OpenNearest : WalkMapEvent
    data object Retry : WalkMapEvent
}
