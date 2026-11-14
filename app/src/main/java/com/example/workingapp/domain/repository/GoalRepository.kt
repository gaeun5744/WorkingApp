package com.example.workingapp.domain.repository

import kotlinx.coroutines.flow.Flow

interface GoalRepository {

    fun observeGoal(): Flow<Int>

    /** 오늘 목표도 함께 바뀐다. 지난날은 그날의 목표를 유지한다. */
    suspend fun setGoal(goal: Int)
}
