package com.example.workingapp.presentation.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(resId, *args.toTypedArray())
}

/** Composable 밖(예: Snackbar 를 띄우는 effect 수집 코루틴)에서 문구를 만들 때 쓴다. */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(resId, *args.toTypedArray())
}
