package com.example.workingapp.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    // 서비스 시간대는 Asia/Seoul 고정 (PRD Q6). 테스트에서는 고정 Clock 으로 교체한다.
    @Singleton
    @Provides
    fun provideClock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
