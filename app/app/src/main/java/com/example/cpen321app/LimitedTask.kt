package com.example.cpen321app

data class LimitedTask(
    val name: String,
    val description: String,
    val priority: String,
    var duration: String,
    val latitude: String,
    val longitude: String
)