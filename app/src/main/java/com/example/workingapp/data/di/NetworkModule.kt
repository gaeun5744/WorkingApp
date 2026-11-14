package com.example.workingapp.data.di

import com.example.workingapp.BuildConfig
import com.example.workingapp.data.remote.api.AuthApi
import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.auth.AuthInterceptor
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

    /** 로깅 + (Phase A) Mock 만 붙은 기본 클라이언트. 토큰 갱신 전용으로 그대로 쓴다. */
    @AuthClient
    @Singleton
    @Provides
    fun provideAuthOkHttpClient(mockApiInterceptor: Lazy<MockApiInterceptor>): OkHttpClient =
        OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    // 토큰이 로그에 남지 않도록 BASIC 레벨 + Authorization 헤더 가림
                    val logging = HttpLoggingInterceptor { message -> Timber.tag("OkHttp").d(message) }
                    logging.level = HttpLoggingInterceptor.Level.BASIC
                    logging.redactHeader(AuthInterceptor.AUTHORIZATION)
                    addInterceptor(logging)
                }
                // 로깅 뒤에 두어야 Mock 응답도 로그에 찍힌다
                if (BuildConfig.USE_MOCK_API) addInterceptor(mockApiInterceptor.get())
            }
            .build()

    /** 일반 API 클라이언트: 맨 앞의 AuthInterceptor 가 토큰을 붙이고 401 이면 갱신 후 재시도한다. */
    @Singleton
    @Provides
    fun provideOkHttpClient(
        @AuthClient authClient: OkHttpClient,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient =
        authClient.newBuilder()
            .apply { interceptors().add(0, authInterceptor) }
            .build()

    @Singleton
    @Provides
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Singleton
    @Provides
    fun providePedometerApi(retrofit: Retrofit): PedometerApi = retrofit.create(PedometerApi::class.java)

    @Singleton
    @Provides
    fun provideAuthApi(@AuthClient client: OkHttpClient, json: Json): AuthApi =
        retrofit(client, json).create(AuthApi::class.java)

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
