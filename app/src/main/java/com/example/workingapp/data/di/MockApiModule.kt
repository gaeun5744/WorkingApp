package com.example.workingapp.data.di

import com.example.workingapp.data.remote.mock.MockApiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object MockApiModule {

    @Singleton
    @Provides
    fun provideMockApiConfig(): MockApiConfig = MockApiConfig(
        latencyMillis = 300L..800L,
        failureRate = 0.0,
        // 401 → 토큰 재발급 흐름을 자주 재현하려고 실제 계약(1시간)보다 짧게 둔다
        accessTokenTtl = Duration.ofMinutes(10),
        refreshTokenTtl = Duration.ofDays(30),
    )
}
