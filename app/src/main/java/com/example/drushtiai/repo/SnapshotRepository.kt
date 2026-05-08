package com.example.drushtiai.repo

import com.example.drushtiai.SupabaseHelper
import com.example.drushtiai.data.CheatingSnapshotRow
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SnapshotRepository {
    private val db get() = SupabaseHelper.client

    suspend fun listForExam(examId: String): List<CheatingSnapshotRow> = withContext(Dispatchers.IO) {
        db.from("cheating_snapshots").select {
            filter { eq("exam_id", examId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<CheatingSnapshotRow>()
    }

    suspend fun deleteSnapshot(snapshotId: String) = withContext(Dispatchers.IO) {
        db.from("cheating_snapshots").delete {
            filter { eq("id", snapshotId) }
        }
    }
}
