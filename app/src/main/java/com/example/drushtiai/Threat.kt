package com.example.drushtiai

data class Threat(
    val cameraName: String,
    val dateTime: String,
    val imageRes: Int // new field for snapshot image
)
