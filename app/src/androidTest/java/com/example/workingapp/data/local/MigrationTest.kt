package com.example.workingapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `app/schemas/` 에 내보낸 스키마로 실제 마이그레이션을 실행해 검증한다. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WorkingAppDatabase::class.java)

    @Test
    fun v1에서_v2로_올리면_기존_걸음은_유지되고_목표는_기본값으로_채워진다() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO daily_steps (date, steps, updated_at, synced) VALUES ('2026-10-24', 8200, 1, 1)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true).use { db ->
            db.query("SELECT steps, synced, goal FROM daily_steps WHERE date = '2026-10-24'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(8200, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(10_000, cursor.getInt(2))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
