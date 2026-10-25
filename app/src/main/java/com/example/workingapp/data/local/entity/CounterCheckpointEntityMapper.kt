package com.example.workingapp.data.local.entity

import com.example.workingapp.domain.step.CounterCheckpoint
import java.time.Instant

fun CounterCheckpointEntity.toDomain(): CounterCheckpoint =
    CounterCheckpoint(rawCount = rawCount, bootCount = bootCount, timestamp = Instant.ofEpochMilli(timestampMillis))

fun CounterCheckpoint.toEntity(): CounterCheckpointEntity =
    CounterCheckpointEntity(rawCount = rawCount, bootCount = bootCount, timestampMillis = timestamp.toEpochMilli())
