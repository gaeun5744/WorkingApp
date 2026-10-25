package com.example.workingapp.data.di

import com.example.workingapp.data.repository.AccountRepositoryImpl
import com.example.workingapp.data.repository.StepRepositoryImpl
import com.example.workingapp.domain.repository.AccountRepository
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
}
