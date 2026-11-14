package com.example.workingapp.testing

import com.example.workingapp.data.local.SessionStore
import com.example.workingapp.data.local.dao.StepDao
import com.example.workingapp.data.remote.api.AuthApi
import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.auth.AccessTokenRenewer
import com.example.workingapp.data.remote.auth.AuthInterceptor
import com.example.workingapp.data.remote.mock.MockApiConfig
import com.example.workingapp.data.remote.mock.MockApiInterceptor
import com.example.workingapp.data.remote.mock.MockNeighborSteps
import com.example.workingapp.data.remote.mock.MockServer
import com.example.workingapp.data.remote.mock.MockTreasureField
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Duration

/**
 * NetworkModule 과 같은 구성(AuthInterceptor → Mock)으로 앱 네트워크 스택을 만든다.
 * 새로 만들 때마다 Mock 서버 상태가 비어 있어 "Mock 프로세스 재시작"을 재현할 수 있다.
 */
internal class MockBackend(clock: Clock, sessionStore: SessionStore, stepDao: StepDao) {

    val json = Json { ignoreUnknownKeys = true }
    private val config = MockApiConfig(
        latencyMillis = LongRange.EMPTY,
        failureRate = 0.0,
        accessTokenTtl = Duration.ofMinutes(10),
        refreshTokenTtl = Duration.ofDays(30),
    )
    private val mock = MockApiInterceptor(MockServer(clock, config, MockNeighborSteps(), MockTreasureField()), config, json)

    private val authClient = OkHttpClient.Builder().addInterceptor(mock).build()
    private val authApi = retrofit(authClient).create(AuthApi::class.java)
    private val client = authClient.newBuilder()
        .apply { interceptors().add(0, AuthInterceptor(sessionStore, AccessTokenRenewer(authApi, sessionStore, stepDao))) }
        .build()

    val api: PedometerApi = retrofit(client).create(PedometerApi::class.java)

    private fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://mock.workingapp.local/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
