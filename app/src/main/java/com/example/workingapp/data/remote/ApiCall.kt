package com.example.workingapp.data.remote

import com.example.workingapp.data.remote.dto.ErrorResponse
import com.example.workingapp.domain.model.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Retrofit 호출 예외를 [AppError] 로 정규화한다. 코루틴 취소는 그대로 전파한다. */
internal suspend fun <T> apiCall(json: Json, block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(e.toAppError(json))
    } catch (_: IOException) {
        Result.failure(AppError.Network)
    } catch (_: SerializationException) {
        Result.failure(AppError.Unknown)
    }

private fun HttpException.toAppError(json: Json): AppError = when (code()) {
    400, 404, 409 -> AppError.InvalidRequest(errorCode(json) ?: "UNKNOWN")
    401 -> AppError.Unauthorized
    in 500..599 -> AppError.Server
    else -> AppError.Unknown
}

private fun HttpException.errorCode(json: Json): String? {
    val body = response()?.errorBody()?.string() ?: return null
    return try {
        json.decodeFromString(ErrorResponse.serializer(), body).code
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
