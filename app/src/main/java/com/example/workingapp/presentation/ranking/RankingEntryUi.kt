package com.example.workingapp.presentation.ranking

data class RankingEntryUi(
    val rank: Int,
    val userId: String,
    val nickname: String,
    val steps: Int,
    val isMe: Boolean,
)
