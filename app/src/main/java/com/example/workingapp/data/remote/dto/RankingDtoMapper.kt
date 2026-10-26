package com.example.workingapp.data.remote.dto

import com.example.workingapp.domain.model.Ranking
import com.example.workingapp.domain.model.RankingEntry
import com.example.workingapp.domain.model.RankingPeriod
import java.time.Instant
import java.time.LocalDate

fun RankingResponse.toDomain(period: RankingPeriod, fetchedAt: Instant): Ranking = Ranking(
    period = period,
    periodStart = LocalDate.parse(periodStart),
    regionName = region.name,
    entries = entries.map { it.toDomain() },
    me = me.toDomain(),
    fetchedAt = fetchedAt,
)

fun RankingEntryDto.toDomain(): RankingEntry =
    RankingEntry(rank = rank, userId = userId, nickname = nickname, steps = steps)
