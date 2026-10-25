package com.example.workingapp.data.remote.api

import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.MeResponse
import com.example.workingapp.data.remote.dto.RankingResponse
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
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * `docs/api.md` 계약의 Retrofit 표현.
 * 인증 헤더와 401 갱신은 `AuthInterceptor` 가 처리한다.
 */
interface PedometerApi {

    @POST("auth/anonymous")
    suspend fun signUpAnonymous(@Body request: AnonymousSignUpRequest): TokenResponse

    @GET("me")
    suspend fun getMe(): MeResponse

    @GET("regions")
    suspend fun searchRegions(@Query("query") query: String): RegionsResponse

    @PUT("me/region")
    suspend fun updateRegion(@Body request: UpdateRegionRequest): MeResponse

    @POST("steps/sync")
    suspend fun syncSteps(@Body request: StepsSyncRequest): StepsSyncResponse

    @GET("rewards/today")
    suspend fun getTodayRewards(): RewardsTodayResponse

    @POST("rewards/claim")
    suspend fun claimReward(@Body request: RewardClaimRequest): RewardClaimResponse

    @GET("wallet")
    suspend fun getWallet(): WalletResponse

    @GET("ranking")
    suspend fun getRanking(@Query("period") period: String): RankingResponse
}
