package com.example.workingapp.data.remote.auth

import com.example.workingapp.data.local.SessionStore
import com.example.workingapp.data.local.dao.StepDao
import com.example.workingapp.data.remote.api.AuthApi
import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.RefreshTokenRequest
import com.example.workingapp.data.remote.dto.TokenResponse
import com.example.workingapp.data.remote.dto.UpdateRegionRequest
import retrofit2.Call
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 401 을 받은 요청을 위해 새 액세스 토큰을 구한다 (AuthInterceptor 에서 OkHttp 스레드로 동기 실행).
 * 1. 다른 요청이 이미 갱신했다면 그 토큰을 쓴다
 * 2. 리프레시 토큰으로 재발급
 * 3. 리프레시가 거부되면 저장된 닉네임·동네로 조용히 재가입하고, 새 계정에 걸음을 다시 올리도록 로컬 걸음을 미동기화로 되돌린다 (PRD Q19)
 */
@Singleton
internal class AccessTokenRenewer @Inject constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val stepDao: StepDao,
) {
    private val lock = Any()

    /** @param failedToken 401 을 받은 요청에 실려 있던 토큰 */
    fun renew(failedToken: String?): String? = synchronized(lock) {
        val current = sessionStore.accessToken
        if (current != null && current != failedToken) return current

        when (val refreshed = refresh()) {
            is Outcome.Renewed -> refreshed.accessToken
            Outcome.Rejected -> reSignUp()
            // 네트워크 문제로 재발급 여부를 모를 때 재가입하면 멀쩡한 계정을 버리게 된다
            Outcome.Unreachable -> null
        }
    }

    private fun refresh(): Outcome {
        val refreshToken = sessionStore.refreshToken ?: return Outcome.Rejected
        return when (val result = authApi.refreshToken(RefreshTokenRequest(refreshToken)).executeSafely()) {
            is CallResult.Success -> Outcome.Renewed(save(result.body))
            is CallResult.HttpError -> if (result.code == HTTP_UNAUTHORIZED) Outcome.Rejected else Outcome.Unreachable
            CallResult.Unreachable -> Outcome.Unreachable
        }
    }

    private fun reSignUp(): String? {
        val nickname = sessionStore.nickname ?: return null
        val signUp = authApi.signUpAnonymous(AnonymousSignUpRequest(nickname)).executeSafely()
        if (signUp !is CallResult.Success) return null
        val accessToken = save(signUp.body)
        sessionStore.region?.let { region ->
            authApi.updateRegion(AuthInterceptor.BEARER_PREFIX + accessToken, UpdateRegionRequest(region.id))
                .executeSafely()
        }
        val resetDays = stepDao.markAllUnsynced()
        Timber.i("계정을 복구할 수 없어 자동 재가입함 — 걸음 %d일치 재업로드 예정", resetDays)
        return accessToken
    }

    private fun save(tokens: TokenResponse): String {
        sessionStore.saveTokens(tokens.accessToken, tokens.refreshToken)
        return tokens.accessToken
    }

    private fun <T> Call<T>.executeSafely(): CallResult<T> =
        try {
            val response = execute()
            val body = response.body()
            if (response.isSuccessful && body != null) CallResult.Success(body) else CallResult.HttpError(response.code())
        } catch (_: IOException) {
            CallResult.Unreachable
        }

    private sealed interface Outcome {
        data class Renewed(val accessToken: String) : Outcome
        data object Rejected : Outcome
        data object Unreachable : Outcome
    }

    private sealed interface CallResult<out T> {
        data class Success<T>(val body: T) : CallResult<T>
        data class HttpError(val code: Int) : CallResult<Nothing>
        data object Unreachable : CallResult<Nothing>
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
