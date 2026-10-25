package com.example.workingapp.data.di

import com.example.workingapp.BuildConfig
import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.mock.MockApiInterceptor
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Singleton
    @Provides
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Singleton
    @Provides
    fun provideOkHttpClient(mockApiInterceptor: Lazy<MockApiInterceptor>): OkHttpClient =
        OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    // 토큰이 로그에 남지 않도록 BASIC 레벨 + Authorization 헤더 가림
                    val logging = HttpLoggingInterceptor { message -> Timber.tag("OkHttp").d(message) }
                    logging.level = HttpLoggingInterceptor.Level.BASIC
                    logging.redactHeader("Authorization")
                    addInterceptor(logging)
                }
                // 로깅 뒤에 두어야 Mock 응답도 로그에 찍힌다
                if (BuildConfig.USE_MOCK_API) addInterceptor(mockApiInterceptor.get())
            }
            .build()

    @Singleton
    @Provides
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Singleton
    @Provides
    fun providePedometerApi(retrofit: Retrofit): PedometerApi = retrofit.create(PedometerApi::class.java)
}
