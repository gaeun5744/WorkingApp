package com.example.workingapp.data.repository

import com.example.workingapp.data.local.GoalStore
import com.example.workingapp.data.local.dao.StepDao
import com.example.workingapp.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

internal class GoalRepositoryImpl @Inject constructor(
    private val goalStore: GoalStore,
    private val dao: StepDao,
    private val clock: Clock,
) : GoalRepository {

    override fun observeGoal(): Flow<Int> = goalStore.goal

    override suspend fun setGoal(goal: Int) {
        goalStore.set(goal)
        dao.updateGoal(LocalDate.now(clock).toString(), goal)
    }
}
