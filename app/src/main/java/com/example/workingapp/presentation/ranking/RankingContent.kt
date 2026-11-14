package com.example.workingapp.presentation.ranking

import com.example.workingapp.presentation.text.UiText
import kotlinx.collections.immutable.ImmutableList
import java.time.LocalTime

sealed interface RankingContent {
    data object Loading : RankingContent

    /** [regionNotSet] 이면 재시도 대신 동네 선택을 안내한다 */
    data class Error(val message: UiText, val regionNotSet: Boolean) : RankingContent

    data class Loaded(
        val regionName: String,
        val entries: ImmutableList<RankingEntryUi>,
        val me: RankingEntryUi,
        /** 이 시각 기준 랭킹 (최대 5분 캐시) */
        val fetchedAt: LocalTime,
    ) : RankingContent
}
