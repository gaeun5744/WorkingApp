package com.example.workingapp.presentation.nav

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import com.example.workingapp.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppNavViewModel @Inject constructor(
    accountRepository: AccountRepository,
) : ViewModel() {

    /** 온보딩(가입 + 동네 설정)을 마치지 않았으면 온보딩부터 시작한다. */
    val startKey: NavKey = if (accountRepository.isOnboarded()) HomeKey else OnboardingKey
}
