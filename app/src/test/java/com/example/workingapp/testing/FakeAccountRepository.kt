package com.example.workingapp.testing

import com.example.workingapp.domain.model.Region
import com.example.workingapp.domain.repository.AccountRepository

class FakeAccountRepository : AccountRepository {

    var updateRegionResult: Result<Unit> = Result.success(Unit)
    val updatedRegions = mutableListOf<Region>()

    override fun isOnboarded(): Boolean = true
    override fun hasSignedUp(): Boolean = true
    override fun savedNickname(): String = "걷는토끼"
    override suspend fun signUp(nickname: String): Result<Unit> = Result.success(Unit)
    override suspend fun searchRegions(query: String): Result<List<Region>> = Result.success(emptyList())

    override suspend fun updateRegion(region: Region): Result<Unit> {
        updatedRegions += region
        return updateRegionResult
    }
}
