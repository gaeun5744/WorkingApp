package com.example.workingapp.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.workingapp.data.local.GoalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/** 테스트마다 새 파일을 쓰는 GoalStore (같은 파일에 DataStore 를 둘 만들면 안 되므로). */
internal fun testGoalStore(context: Context): GoalStore = GoalStore(
    PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        File(context.cacheDir, "test_settings_${UUID.randomUUID()}.preferences_pb")
    },
)
