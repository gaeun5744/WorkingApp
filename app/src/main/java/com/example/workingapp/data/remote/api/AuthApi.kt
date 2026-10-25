package com.example.workingapp.data.remote.api

import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.MeResponse
import com.example.workingapp.data.remote.dto.RefreshTokenRequest
import com.example.workingapp.data.remote.dto.TokenResponse
import com.example.workingapp.data.remote.dto.UpdateRegionRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * 토큰 갱신·자동 재가입 전용 API. OkHttp 인터셉터는 동기 함수라 suspend 대신 [Call] 을 쓰고,
 * AuthInterceptor 가 붙지 않은 별도 클라이언트로 호출해 갱신 요청이 다시 갱신을 부르지 않게 한다.
 */
interface AuthApi {

    @POST("auth/anonymous")
    fun signUpAnonymous(@Body request: AnonymousSignUpRequest): Call<TokenResponse>

    @POST("auth/refresh")
    fun refreshToken(@Body request: RefreshTokenRequest): Call<TokenResponse>

    @PUT("me/region")
    fun updateRegion(
        @Header("Authorization") authorization: String,
        @Body request: UpdateRegionRequest,
    ): Call<MeResponse>
}
