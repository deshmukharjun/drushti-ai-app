package com.example.drushtiai

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP client for the FastAPI backend that runs YOLO/MediaPipe.
 *
 * The phone calls these endpoints to:
 *   1. List or register the camera that should be used for an exam
 *   2. Start the detection stream tagged with the exam_id (so the backend
 *      uploads each saved snapshot to Supabase under that exam)
 *   3. Stop the stream when the invigilator ends surveillance
 */
object BackendApi {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            // Generous timeouts — phone-to-laptop traffic over Wi-Fi can stall
            // briefly, and the first /start request kicks off camera + ML init.
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
    }

    private val baseUrl: String
        get() = BuildConfig.BACKEND_URL.trimEnd('/')

    private val defaultCameraSource: String
        get() = BuildConfig.BACKEND_CAMERA_SOURCE

    @Serializable
    data class CameraInfo(
        val id: String,
        val name: String,
        @SerialName("stream_url") val streamUrl: String,
        val location: String = "",
        @SerialName("is_active") val isActive: Boolean = true,
        val status: String = "disconnected",
        @SerialName("created_at") val createdAt: String = ""
    )

    @Serializable
    private data class CameraCreateRequest(
        val name: String,
        @SerialName("stream_url") val streamUrl: String,
        val location: String = ""
    )

    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Failure(val message: String) : Result<Nothing>()
    }

    /**
     * Health check — used to surface a clear error if BACKEND_URL is wrong
     * or the backend isn't running, before anything else is attempted.
     */
    suspend fun ping(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resp = client.get("$baseUrl/")
            if (resp.status == HttpStatusCode.OK) {
                Result.Success(resp.bodyAsText())
            } else {
                Result.Failure("Backend returned ${resp.status}")
            }
        } catch (e: Exception) {
            Result.Failure("Cannot reach backend at $baseUrl: ${e.message}")
        }
    }

    suspend fun listCameras(): Result<List<CameraInfo>> = withContext(Dispatchers.IO) {
        try {
            val resp = client.get("$baseUrl/api/cameras")
            if (resp.status != HttpStatusCode.OK) {
                return@withContext Result.Failure("List cameras failed: ${resp.status}")
            }
            val cams = json.decodeFromString<List<CameraInfo>>(resp.bodyAsText())
            Result.Success(cams)
        } catch (e: Exception) {
            Result.Failure("listCameras error: ${e.message}")
        }
    }

    suspend fun registerCamera(
        name: String,
        streamUrl: String,
        location: String = ""
    ): Result<CameraInfo> = withContext(Dispatchers.IO) {
        try {
            val resp = client.post("$baseUrl/api/cameras") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    CameraCreateRequest.serializer(),
                    CameraCreateRequest(name, streamUrl, location)
                ))
            }
            if (resp.status.value !in 200..299) {
                return@withContext Result.Failure(
                    "Register camera failed: ${resp.status} ${resp.bodyAsText()}"
                )
            }
            Result.Success(json.decodeFromString<CameraInfo>(resp.bodyAsText()))
        } catch (e: Exception) {
            Result.Failure("registerCamera error: ${e.message}")
        }
    }

    /**
     * Make sure a camera exists on the backend, returning its id. If none
     * are registered yet, register the default device camera (configured via
     * `backend.camera.source` in local.properties — defaults to "0").
     */
    suspend fun ensureCameraJoined(): Result<CameraInfo> {
        return when (val list = listCameras()) {
            is Result.Failure -> list
            is Result.Success -> {
                list.value.firstOrNull()?.let { Result.Success(it) }
                    ?: registerCamera(
                        name = "DrushtiAI Default",
                        streamUrl = defaultCameraSource,
                        location = "Auto-registered from app"
                    )
            }
        }
    }

    /**
     * Start the YOLO/MediaPipe stream for a camera, tagging every saved
     * snapshot with this exam_id so it lands in cheating_snapshots for the app.
     */
    suspend fun startStream(cameraId: String, examId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val resp = client.post(
                    "$baseUrl/api/cameras/$cameraId/start?exam_id=$examId"
                )
                if (resp.status.value !in 200..299) {
                    Result.Failure("Start stream failed: ${resp.status} ${resp.bodyAsText()}")
                } else {
                    Result.Success(Unit)
                }
            } catch (e: Exception) {
                Result.Failure("startStream error: ${e.message}")
            }
        }

    suspend fun stopStream(cameraId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val resp = client.post("$baseUrl/api/cameras/$cameraId/stop")
            if (resp.status.value !in 200..299) {
                Result.Failure("Stop stream failed: ${resp.status} ${resp.bodyAsText()}")
            } else {
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure("stopStream error: ${e.message}")
        }
    }

    @Suppress("unused")
    suspend fun unregisterCamera(cameraId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val resp = client.delete("$baseUrl/api/cameras/$cameraId")
            if (resp.status.value !in 200..299) {
                Result.Failure("Unregister failed: ${resp.status}")
            } else {
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Failure("unregisterCamera error: ${e.message}")
        }
    }
}
