package com.example.workingapp.presentation.text

import androidx.annotation.StringRes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 사용자에게 보여줄 문구의 i18n-safe 표현.
 * ViewModel 은 문자열 리소스 ID 와 인자만 전달하고, 실제 문자열은 Composable 에서 [asString] 으로 만든다.
 */
sealed interface UiText {
    data class Resource(
        @param:StringRes val resId: Int,
        val args: ImmutableList<Any> = persistentListOf(),
    ) : UiText
}
