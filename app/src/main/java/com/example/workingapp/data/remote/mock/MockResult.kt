package com.example.workingapp.data.remote.mock

/** Mock 서버 처리 결과. HTTP 응답으로 바꾸는 일은 [MockApiInterceptor] 가 맡는다. */
internal sealed interface MockResult<out T> {
    data class Success<T>(val body: T, val httpStatus: Int = 200) : MockResult<T>
    data class Failure(val httpStatus: Int, val code: String, val message: String) : MockResult<Nothing>
}
