package com.example.workingapp.domain.model

/** Repository 가 실패를 상위 레이어에 전달하는 형태. raw 네트워크 예외는 data 레이어 밖으로 나가지 않는다. */
sealed class AppError : Exception() {
    /** 네트워크 끊김·타임아웃 — 재시도하면 성공할 수 있다 */
    data object Network : AppError()

    /** 서버 5xx — 재시도하면 성공할 수 있다 */
    data object Server : AppError()

    /** 인증을 복구하지 못함 */
    data object Unauthorized : AppError()

    /** 요청 값 오류 (400). [code] 는 `docs/api.md` 에러 코드 */
    data class InvalidRequest(val code: String) : AppError()

    data object Unknown : AppError()
}
