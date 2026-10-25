package com.example.workingapp.presentation.region

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workingapp.domain.repository.AccountRepository
import com.example.workingapp.presentation.text.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RegionSelectViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val retryCount = MutableStateFlow(0)

    // 입력이 멈춘 뒤에 검색하고(debounce), 새 검색어가 오면 이전 검색 결과는 버린다(flatMapLatest)
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RegionSelectUiState> =
        combine(_query.debounce(SEARCH_DEBOUNCE_MILLIS), retryCount) { query, _ -> query }
            .flatMapLatest { query ->
                flow {
                    emit(RegionSelectUiState.Loading)
                    emit(
                        accountRepository.searchRegions(query).fold(
                            onSuccess = { RegionSelectUiState.Success(it.toImmutableList()) },
                            onFailure = { RegionSelectUiState.Error(it.toUiText()) },
                        ),
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegionSelectUiState.Loading)

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onRetry() {
        retryCount.value++
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
