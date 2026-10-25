package com.example.workingapp.data.di

import android.content.Context
import androidx.room.Room
import com.example.workingapp.data.local.WorkingAppDatabase
import com.example.workingapp.data.local.dao.StepDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): WorkingAppDatabase =
        Room.databaseBuilder(context, WorkingAppDatabase::class.java, "working_app.db").build()

    @Provides
    fun provideStepDao(database: WorkingAppDatabase): StepDao = database.stepDao()
}
