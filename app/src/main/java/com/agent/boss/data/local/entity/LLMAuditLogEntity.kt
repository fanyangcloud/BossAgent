package com.agent.boss.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "llm_audit_logs",
    indices = [
        Index(value = ["job_id"]),
        Index(value = ["created_at"])
    ]
)
data class LLMAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "scene")
    val scene: String,

    @ColumnInfo(name = "job_id")
    val jobId: String = "",

    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "prompt_snapshot")
    val promptSnapshot: String,

    @ColumnInfo(name = "response_raw")
    val responseRaw: String,

    @ColumnInfo(name = "prompt_tokens")
    val promptTokens: Int = 0,

    @ColumnInfo(name = "completion_tokens")
    val completionTokens: Int = 0,

    @ColumnInfo(name = "total_tokens")
    val totalTokens: Int = 0,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    @ColumnInfo(name = "is_success")
    val isSuccess: Boolean = true,

    @ColumnInfo(name = "error_message")
    val errorMessage: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCENE_EVALUATE = "JD_EVALUATE"
        const val SCENE_GREETING = "GREETING_GEN"
        const val SCENE_INTENT = "INTENT_CLASSIFY"
    }
}
