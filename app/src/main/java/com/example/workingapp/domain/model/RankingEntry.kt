package com.example.workingapp.domain.model

data class RankingEntry(
    val rank: Int,
    val userId: String,
    val nickname: String,
    val steps: Int,
)
