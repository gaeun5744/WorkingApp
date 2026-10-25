package com.example.workingapp.data.remote.api

import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.MeResponse
import com.example.workingapp.data.remote.dto.RankingResponse
import com.example.workingapp.data.remote.dto.RefreshTokenRequest
import com.example.workingapp.data.remote.dto.RegionsResponse
import com.example.workingapp.data.remote.dto.RewardClaimRequest
import com.example.workingapp.data.remote.dto.RewardClaimResponse
import com.example.workingapp.data.remote.dto.RewardsTodayResponse
import com.example.workingapp.data.remote.dto.StepsSyncRequest
import com.example.workingapp.data.remote.dto.StepsSyncResponse
import com.example.workingapp.data.remote.dto.TokenResponse
import com.example.workingapp.data.remote.dto.UpdateRegionRequest
import com.example.workingapp.data.remote.dto.WalletResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * `docs/api.md` 계약의 Retrofit 표현.
 * 인증 헤더는 M3 에서 OkHttp Interceptor/Authenticator 로 옮기기 전까지 파라미터로 받는다.
 */
interface PedometerApi {

    @POST("auth/anonymous")
    suspend fun signUpAnonymous(@Body request: AnonymousSignUpRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): TokenResponse

    @GET("me")
    suspend fun getMe(@Header("Authorization") authorization: String): MeResponse

    @GET("regions")
    suspend fun searchRegions(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
    ): RegionsResponse

    @PUT("me/region")
    suspend fun updateRegion(
        @Header("Authorization") authorization: String,
        @Body request: UpdateRegionRequest,
    ): MeResponse

    @POST("steps/sync")
    suspend fun syncSteps(
        @Header("Authorization") authorization: String,
        @Body request: StepsSyncRequest,
    ): StepsSyncResponse

    @GET("rewards/today")
    suspend fun getTodayRewards(@Header("Authorization") authorization: String): RewardsTodayResponse

    @POST("rewards/claim")
    suspend fun claimReward(
        @Header("Authorization") authorization: String,
        @Body request: RewardClaimRequest,
    ): RewardClaimResponse

    @GET("wallet")
    suspend fun getWallet(@Header("Authorization") authorization: String): WalletResponse

    @GET("ranking")
    suspend fun getRanking(
        @Header("Authorization") authorization: String,
        @Query("period") period: String,
    ): RankingResponse
}
