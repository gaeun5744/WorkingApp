package com.example.workingapp.presentation.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(resId, *args.toTypedArray())
}
