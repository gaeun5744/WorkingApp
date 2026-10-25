package com.example.workingapp.data.repository

import com.example.workingapp.data.local.SessionStore
import com.example.workingapp.data.remote.api.PedometerApi
import com.example.workingapp.data.remote.apiCall
import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.UpdateRegionRequest
import com.example.workingapp.data.remote.dto.toDomain
import com.example.workingapp.domain.model.Region
import com.example.workingapp.domain.repository.AccountRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal class AccountRepositoryImpl @Inject constructor(
    private val api: PedometerApi,
    private val sessionStore: SessionStore,
    private val json: Json,
) : AccountRepository {

    override fun isOnboarded(): Boolean = hasSignedUp() && sessionStore.region != null

    override fun hasSignedUp(): Boolean = sessionStore.refreshToken != null

    override fun savedNickname(): String? = sessionStore.nickname

    override suspend fun signUp(nickname: String): Result<Unit> =
        apiCall(json) { api.signUpAnonymous(AnonymousSignUpRequest(nickname)) }.map { tokens ->
            sessionStore.saveTokens(tokens.accessToken, tokens.refreshToken)
            sessionStore.saveNickname(nickname)
        }

    override suspend fun searchRegions(query: String): Result<List<Region>> =
        apiCall(json) { api.searchRegions(query) }.map { response -> response.regions.map { it.toDomain() } }

    override suspend fun updateRegion(region: Region): Result<Unit> =
        apiCall(json) { api.updateRegion(UpdateRegionRequest(region.id)) }.map { sessionStore.saveRegion(region) }
}
