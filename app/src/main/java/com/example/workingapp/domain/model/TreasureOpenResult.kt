package com.example.workingapp.domain.model

data class TreasureOpenResult(
    val treasureId: String,
    val points: Int,
    val balance: Int,
    val alreadyOpened: Boolean,
)
