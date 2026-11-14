package com.example.workingapp.data.remote.auth

import com.example.workingapp.data.local.SessionStore
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

/**
 * 저장된 액세스 토큰을 `Authorization` 헤더로 붙이고, 401 이면 토큰을 갱신해 한 번만 다시 요청한다.
 * 인증 API(`/auth/` 경로)는 토큰이 필요 없다.
 *
 * OkHttp `Authenticator` 를 쓰지 않는 이유: Authenticator 는 애플리케이션 인터셉터 뒤에서 동작해서,
 * Mock 처럼 애플리케이션 인터셉터가 바로 돌려준 401 은 보지 못한다 (실측: 호출 0회).
 * 인터셉터에서 처리하면 Mock 과 실제 서버에서 똑같이 동작한다.
 */
internal class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
    private val renewer: AccessTokenRenewer,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.startsWith(AUTH_PATH_PREFIX)) return chain.proceed(request)

        val token = sessionStore.accessToken
        val response = chain.proceed(request.withToken(token))
        if (response.code != HTTP_UNAUTHORIZED) return response

        val newToken = renewer.renew(failedToken = token) ?: return response
        response.close()
        return chain.proceed(request.withToken(newToken))
    }

    private fun Request.withToken(token: String?): Request =
        if (token == null) this else newBuilder().header(AUTHORIZATION, BEARER_PREFIX + token).build()

    companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val AUTH_PATH_PREFIX = "/auth/"
        private const val HTTP_UNAUTHORIZED = 401
    }
}
