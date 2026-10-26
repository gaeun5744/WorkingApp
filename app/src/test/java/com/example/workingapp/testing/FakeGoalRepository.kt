package com.example.workingapp.testing

import com.example.workingapp.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGoalRepository(initialGoal: Int = 10_000) : GoalRepository {
    val goal = MutableStateFlow(initialGoal)
    override fun observeGoal(): Flow<Int> = goal
    override suspend fun setGoal(goal: Int) {
        this.goal.value = goal
    }
}
