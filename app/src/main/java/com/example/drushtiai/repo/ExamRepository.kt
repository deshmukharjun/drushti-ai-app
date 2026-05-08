package com.example.drushtiai.repo

import com.example.drushtiai.SupabaseHelper
import com.example.drushtiai.data.ExamInsert
import com.example.drushtiai.data.ExamRow
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExamRepository {
    private val db get() = SupabaseHelper.client

    suspend fun listExamsForUser(userId: String): List<ExamRow> = withContext(Dispatchers.IO) {
        db.from("exams").select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<ExamRow>()
    }

    suspend fun getExam(examId: String): ExamRow? = withContext(Dispatchers.IO) {
        db.from("exams").select {
            filter { eq("id", examId) }
        }.decodeList<ExamRow>().firstOrNull()
    }

    suspend fun insertExam(row: ExamInsert): ExamRow = withContext(Dispatchers.IO) {
        db.from("exams").insert(row) {
            select(Columns.ALL)
        }.decodeSingle<ExamRow>()
    }

    suspend fun updateExam(row: ExamRow) = withContext(Dispatchers.IO) {
        require(!row.id.isNullOrBlank())
        db.from("exams").update(row) {
            filter { eq("id", row.id!!) }
        }
    }

    suspend fun setCameraLinked(examId: String, devicePayload: String) = withContext(Dispatchers.IO) {
        db.from("exams").update({
            set("camera_connected", true)
            set("linked_device_id", devicePayload)
        }) {
            filter { eq("id", examId) }
        }
    }

    suspend fun setStatus(examId: String, status: String) = withContext(Dispatchers.IO) {
        db.from("exams").update({
            set("status", status)
        }) {
            filter { eq("id", examId) }
        }
    }
}
