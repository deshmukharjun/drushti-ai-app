package com.example.drushtiai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ExamInsert(
    @SerialName("user_id") val userId: String,
    val subject: String,
    @SerialName("exam_date") val examDate: String,
    @SerialName("exam_time") val examTime: String,
    @SerialName("student_count") val studentCount: Int,
    @SerialName("room_notes") val roomNotes: String? = null,
    val status: String = "draft",
    @SerialName("camera_connected") val cameraConnected: Boolean = false,
    @SerialName("linked_device_id") val linkedDeviceId: String? = null
)

@Serializable
data class ExamRow(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val subject: String,
    @SerialName("exam_date") val examDate: String,
    @SerialName("exam_time") val examTime: String,
    @SerialName("student_count") val studentCount: Int,
    @SerialName("room_notes") val roomNotes: String? = null,
    val status: String = "draft",
    @SerialName("camera_connected") val cameraConnected: Boolean = false,
    @SerialName("linked_device_id") val linkedDeviceId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CheatingSnapshotRow(
    val id: String? = null,
    @SerialName("exam_id") val examId: String,
    @SerialName("image_url") val imageUrl: String,
    val label: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
