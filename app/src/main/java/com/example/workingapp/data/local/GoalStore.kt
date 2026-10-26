package com.example.workingapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 일일 목표 걸음. 바뀌면 화면에 바로 반영되도록 Flow 를 주는 DataStore 에 둔다.
 * (토큰은 인터셉터가 동기로 읽어야 해서 SessionStore(SharedPreferences) 에 둔다)
 */
@Singleton
internal class GoalStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val goal: Flow<Int> = dataStore.data.map { it[KEY_GOAL] ?: DEFAULT_GOAL }.distinctUntilChanged()

    suspend fun current(): Int = goal.first()

    suspend fun set(goal: Int) {
        dataStore.edit { it[KEY_GOAL] = goal }
    }

    companion object {
        const val DEFAULT_GOAL = 10_000
        private val KEY_GOAL = intPreferencesKey("daily_goal")
    }
}
