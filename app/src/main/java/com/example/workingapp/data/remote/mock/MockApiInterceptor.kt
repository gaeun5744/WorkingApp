package com.example.workingapp.data.remote.mock

import com.example.workingapp.data.remote.dto.AnonymousSignUpRequest
import com.example.workingapp.data.remote.dto.ErrorResponse
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 실제 네트워크로 나가지 않고 [MockServer] 응답을 HTTP 응답으로 돌려주는 Interceptor (PRD Q18).
 * 앱의 Retrofit·DTO·인증 처리 코드는 실제 서버를 쓸 때와 똑같이 동작한다.
 */
@Singleton
internal class MockApiInterceptor @Inject constructor(
    private val server: MockServer,
    private val config: MockApiConfig,
    private val json: Json,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        simulateLatency()
        if (Random.nextDouble() < config.failureRate) {
            return request.respondFailure(MockResult.Failure(500, "INTERNAL_ERROR", "Mock 오류 주입"))
        }
        return route(request)
    }

    private fun route(request: Request): Response {
        val path = request.url.encodedPath
        val authorization = request.header(AUTHORIZATION)
        return when (request.method to path) {
            "POST" to "/auth/anonymous" -> request.withBody(AnonymousSignUpRequest.serializer()) { body ->
                request.respond(server.signUpAnonymous(body.nickname), TokenResponse.serializer())
            }
            "POST" to "/auth/refresh" -> request.withBody(RefreshTokenRequest.serializer()) { body ->
                request.respond(server.refreshToken(body.refreshToken), TokenResponse.serializer())
            }
            "GET" to "/me" -> request.authorized(authorization) { userId ->
                request.respond(server.getMe(userId), MeResponse.serializer())
            }
            "GET" to "/regions" -> request.authorized(authorization) {
                val query = request.url.queryParameter("query").orEmpty()
                request.respond(server.searchRegions(query), RegionsResponse.serializer())
            }
            "PUT" to "/me/region" -> request.authorized(authorization) { userId ->
                request.withBody(UpdateRegionRequest.serializer()) { body ->
                    request.respond(server.updateRegion(userId, body.regionId), MeResponse.serializer())
                }
            }
            "POST" to "/steps/sync" -> request.authorized(authorization) { userId ->
                request.withBody(StepsSyncRequest.serializer()) { body ->
                    request.respond(server.syncSteps(userId, body), StepsSyncResponse.serializer())
                }
            }
            "GET" to "/rewards/today" -> request.authorized(authorization) { userId ->
                request.respond(server.getTodayRewards(userId), RewardsTodayResponse.serializer())
            }
            "POST" to "/rewards/claim" -> request.authorized(authorization) { userId ->
                request.withBody(RewardClaimRequest.serializer()) { body ->
                    request.respond(server.claimReward(userId, body), RewardClaimResponse.serializer())
                }
            }
            "GET" to "/wallet" -> request.authorized(authorization) { userId ->
                request.respond(server.getWallet(userId), WalletResponse.serializer())
            }
            "GET" to "/ranking" -> request.authorized(authorization) { userId ->
                val period = request.url.queryParameter("period").orEmpty()
                request.respond(server.getRanking(userId, period), RankingResponse.serializer())
            }
            else -> request.respondFailure(MockResult.Failure(404, "NOT_FOUND", "${request.method} $path"))
        }
    }

    private fun Request.authorized(authorization: String?, handle: (userId: String) -> Response): Response =
        when (val auth = server.authenticate(authorization)) {
            is MockResult.Success -> handle(auth.body)
            is MockResult.Failure -> respondFailure(auth)
        }

    private fun <T> Request.withBody(serializer: KSerializer<T>, handle: (T) -> Response): Response {
        val raw = body?.let { requestBody -> Buffer().also(requestBody::writeTo).readUtf8() }.orEmpty()
        val decoded = try {
            json.decodeFromString(serializer, raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return decoded?.let(handle)
            ?: respondFailure(MockResult.Failure(400, "VALIDATION_ERROR", "요청 본문을 읽을 수 없습니다."))
    }

    private fun <T> Request.respond(result: MockResult<T>, serializer: KSerializer<T>): Response =
        when (result) {
            is MockResult.Success -> buildResponse(result.httpStatus, json.encodeToString(serializer, result.body))
            is MockResult.Failure -> respondFailure(result)
        }

    private fun Request.respondFailure(failure: MockResult.Failure): Response =
        buildResponse(
            failure.httpStatus,
            json.encodeToString(ErrorResponse.serializer(), ErrorResponse(failure.code, failure.message)),
        )

    private fun Request.buildResponse(code: Int, body: String): Response =
        Response.Builder()
            .request(this)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code < 400) "OK" else "Error")
            .body(body.toResponseBody(JSON_MEDIA_TYPE))
            .build()

    private fun simulateLatency() {
        val millis = if (config.latencyMillis.isEmpty()) 0L else config.latencyMillis.random()
        // OkHttp 의 백그라운드 스레드에서 실행되므로 sleep 으로 네트워크 지연을 흉내 낸다
        if (millis > 0) Thread.sleep(millis)
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
