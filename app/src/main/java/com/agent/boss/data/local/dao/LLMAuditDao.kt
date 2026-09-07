package com.agent.boss.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.agent.boss.data.local.entity.LLMAuditLogEntity

@Dao
interface LLMAuditDao {

    @Insert
    suspend fun insertLog(log: LLMAuditLogEntity): Long

    @Query("SELECT COALESCE(SUM(total_tokens), 0) FROM llm_audit_logs WHERE created_at >= :startTime AND created_at <= :endTime")
    suspend fun getTodayTotalTokens(startTime: Long, endTime: Long): Int

    @Query("SELECT COALESCE(SUM(prompt_tokens), 0) FROM llm_audit_logs WHERE created_at >= :startTime AND created_at <= :endTime")
    suspend fun getTodayPromptTokens(startTime: Long, endTime: Long): Int

    @Query("SELECT COALESCE(SUM(completion_tokens), 0) FROM llm_audit_logs WHERE created_at >= :startTime AND created_at <= :endTime")
    suspend fun getTodayCompletionTokens(startTime: Long, endTime: Long): Int

    @Query("SELECT * FROM llm_audit_logs ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<LLMAuditLogEntity>

    @Query("DELETE FROM llm_audit_logs WHERE created_at < :beforeTimestamp")
    suspend fun cleanLogsBefore(beforeTimestamp: Long)
}
