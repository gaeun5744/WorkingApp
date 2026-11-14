package com.example.workingapp.data.di

import com.example.workingapp.data.location.FusedLocationRepository
import com.example.workingapp.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: FusedLocationRepository): LocationRepository
}
