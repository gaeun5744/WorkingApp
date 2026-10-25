package com.example.workingapp.domain.repository

import com.example.workingapp.domain.model.Region

interface AccountRepository {

    /** 가입했고 동네까지 설정했는지 */
    fun isOnboarded(): Boolean

    fun hasSignedUp(): Boolean

    fun savedNickname(): String?

    suspend fun signUp(nickname: String): Result<Unit>

    suspend fun searchRegions(query: String): Result<List<Region>>

    suspend fun updateRegion(region: Region): Result<Unit>
}
