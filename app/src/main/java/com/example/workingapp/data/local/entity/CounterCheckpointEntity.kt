package com.example.workingapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 직전에 반영한 센서 측정값 (항상 1행).
 * 걸음 증분과 같은 트랜잭션에서 갱신해야 해서 DataStore 가 아니라 Room 에 둔다.
 */
@Entity(tableName = "counter_checkpoint")
data class CounterCheckpointEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "raw_count") val rawCount: Long,
    @ColumnInfo(name = "boot_count") val bootCount: Int,
    @ColumnInfo(name = "timestamp") val timestampMillis: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
