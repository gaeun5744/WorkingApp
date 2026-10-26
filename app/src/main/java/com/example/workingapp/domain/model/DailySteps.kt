package com.example.workingapp.domain.model

import java.time.LocalDate

/** [goal] 은 그날 기준 목표다. 목표를 바꿔도 지난날의 달성 여부는 바뀌지 않는다. */
data class DailySteps(
    val date: LocalDate,
    val steps: Int,
    val goal: Int,
) {
    val isGoalAchieved: Boolean get() = steps >= goal
}
