package com.example.cpen321app

data class LimitedTask(
    val name: String,
    val description: String,
    val priority: String,
    var duration: String,
    val latitude: String,
    val longitude: String
)

data class LessLimitedTask(
    val id: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val duration: Int,
    val latitude: Double,
    val longitude: Double,
    val priority: Int,
    val description: String
)