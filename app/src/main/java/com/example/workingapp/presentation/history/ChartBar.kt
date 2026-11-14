package com.example.workingapp.presentation.history

/** 막대 하나. 라벨(시·요일·일)은 기간과 순서로 Screen 이 만든다 (ViewModel 은 문자열을 만들지 않는다). */
data class ChartBar(
    val steps: Int,
    val achieved: Boolean,
)
