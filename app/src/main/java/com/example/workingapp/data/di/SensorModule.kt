package com.example.workingapp.data.di

import com.example.workingapp.BuildConfig
import com.example.workingapp.data.sensor.AndroidStepDataSource
import com.example.workingapp.data.sensor.SimulatedStepDataSource
import com.example.workingapp.data.sensor.StepDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object SensorModule {

    @Singleton
    @Provides
    fun provideStepDataSource(
        android: Provider<AndroidStepDataSource>,
        simulated: Provider<SimulatedStepDataSource>,
    ): StepDataSource = if (BuildConfig.USE_FAKE_STEP_SENSOR) simulated.get() else android.get()
}
