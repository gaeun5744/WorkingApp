package com.example.workingapp.data.remote.dto

import com.example.workingapp.domain.model.Region

fun RegionDto.toDomain(): Region = Region(id = id, name = name)
