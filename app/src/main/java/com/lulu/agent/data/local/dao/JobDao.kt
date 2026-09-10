package com.lulu.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lulu.agent.data.local.entity.JobEntity

@Dao
interface JobDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(job: JobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(job: JobEntity): Long

    @Update
    suspend fun update(job: JobEntity)

    @Query("SELECT * FROM jobs WHERE job_id = :jobId LIMIT 1")
    suspend fun findByJobId(jobId: String): JobEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM jobs WHERE job_id = :jobId)")
    suspend fun isJobExists(jobId: String): Boolean

    // 🌟【修改】：已投递 (DELIVERED) 的岗位同样视为已沟通过，防止次日重复扫描发起
    @Query("SELECT EXISTS(SELECT 1 FROM jobs WHERE job_id = :jobId AND status IN ('COMMUNICATED', 'DELIVERED'))")
    suspend fun isJobCommunicated(jobId: String): Boolean

    // 🌟【修改】：沟通总量统计兼顾 COMMUNICATED 与 DELIVERED，确保每日打招呼上限配额准确扣减
    @Query("SELECT COUNT(1) FROM jobs WHERE status IN ('COMMUNICATED', 'DELIVERED') AND updated_at >= :startTime AND updated_at <= :endTime")
    suspend fun getCommunicatedCountBetween(startTime: Long, endTime: Long): Int

    // 维持原样：专门统计投递成功的数量
    @Query("SELECT COUNT(1) FROM jobs WHERE status IN ('DELIVERED', 'RESUME_SENT') AND updated_at >= :startTime AND updated_at <= :endTime")
    suspend fun getDeliveredCountBetween(startTime: Long, endTime: Long): Int

    @Query("UPDATE jobs SET status = :status, updated_at = :updatedAt WHERE job_id = :jobId")
    suspend fun updateStatus(jobId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE jobs 
        SET match_score = :score, 
            eval_reason = :reason, 
            suggested_greeting = :greeting, 
            status = :status, 
            updated_at = :updatedAt 
        WHERE job_id = :jobId
    """)
    suspend fun updateEvaluationResult(
        jobId: String,
        score: Int,
        reason: String,
        greeting: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM jobs ORDER BY updated_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecentJobs(limit: Int, offset: Int = 0): List<JobEntity>
}
