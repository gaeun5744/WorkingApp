package com.example.workingapp.data.di

import com.example.workingapp.data.repository.AccountRepositoryImpl
import com.example.workingapp.data.repository.GoalRepositoryImpl
import com.example.workingapp.data.repository.RewardRepositoryImpl
import com.example.workingapp.data.repository.StepRepositoryImpl
import com.example.workingapp.domain.repository.AccountRepository
import com.example.workingapp.domain.repository.GoalRepository
import com.example.workingapp.domain.repository.RewardRepository
import com.example.workingapp.domain.repository.StepRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStepRepository(impl: StepRepositoryImpl): StepRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository

    @Binds
    @Singleton
    abstract fun bindRewardRepository(impl: RewardRepositoryImpl): RewardRepository
}
