package com.example.workingapp.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import com.example.workingapp.presentation.text.UiText
import com.example.workingapp.presentation.text.asString
import com.example.workingapp.ui.theme.WorkingAppTheme

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onNicknameChange: (String) -> Unit,
    onSubmitNickname: () -> Unit,
    onSelectRegion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.step) {
            OnboardingStep.NICKNAME -> NicknameStep(state, onNicknameChange, onSubmitNickname)
            OnboardingStep.REGION -> RegionStep(state, onSelectRegion)
        }
        state.error?.let { error ->
            Text(text = error.asString(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (state.isSubmitting) CircularProgressIndicator()
    }
}

@Composable
private fun NicknameStep(state: OnboardingUiState, onNicknameChange: (String) -> Unit, onSubmit: () -> Unit) {
    Text(text = stringResource(R.string.onboarding_nickname_title), style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = state.nickname,
        onValueChange = onNicknameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.onboarding_nickname_label)) },
        supportingText = {
            Text(stringResource(R.string.onboarding_nickname_counter, state.nickname.length, OnboardingUiState.MAX_NICKNAME_LENGTH))
        },
        singleLine = true,
        enabled = !state.isSubmitting,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
    Button(onClick = onSubmit, enabled = state.canSubmitNickname, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_next))
    }
}

@Composable
private fun RegionStep(state: OnboardingUiState, onSelectRegion: () -> Unit) {
    Text(
        text = stringResource(R.string.onboarding_region_title, state.nickname),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Text(text = stringResource(R.string.onboarding_region_description), textAlign = TextAlign.Center)
    Button(onClick = onSelectRegion, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_select_region))
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingNicknamePreview() {
    WorkingAppTheme {
        OnboardingScreen(
            state = OnboardingUiState(step = OnboardingStep.NICKNAME, nickname = "걷는토끼"),
            onNicknameChange = {},
            onSubmitNickname = {},
            onSelectRegion = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingRegionErrorPreview() {
    WorkingAppTheme {
        OnboardingScreen(
            state = OnboardingUiState(
                step = OnboardingStep.REGION,
                nickname = "걷는토끼",
                error = UiText.Resource(R.string.error_network),
            ),
            onNicknameChange = {},
            onSubmitNickname = {},
            onSelectRegion = {},
        )
    }
}
