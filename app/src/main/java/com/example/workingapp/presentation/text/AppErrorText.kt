package com.example.workingapp.presentation.text

import com.example.workingapp.R
import com.example.workingapp.domain.model.AppError

/** 오류 → 사용자 문구 매핑은 이 한 곳에서만 한다. */
fun Throwable.toUiText(): UiText = UiText.Resource(
    when (this) {
        AppError.Network -> R.string.error_network
        AppError.Server -> R.string.error_server
        else -> R.string.error_unknown
    },
)
