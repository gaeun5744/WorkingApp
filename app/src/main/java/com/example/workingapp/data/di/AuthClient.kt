package com.example.workingapp.data.di

import javax.inject.Qualifier

/** 토큰 갱신 전용 클라이언트 (AuthInterceptor 없음). 갱신 요청이 다시 갱신을 부르는 재귀를 막는다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AuthClient
