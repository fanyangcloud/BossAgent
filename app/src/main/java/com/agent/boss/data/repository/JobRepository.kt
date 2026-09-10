package com.agent.boss.data.repository

import com.agent.boss.data.local.AppDatabase
import com.agent.boss.data.local.entity.CompanyEntity
import com.agent.boss.data.local.entity.JobEntity
import com.agent.boss.data.local.entity.LLMAuditLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class JobRepository(private val db: AppDatabase) {

    private val jobDao = db.jobDao()
    private val companyDao = db.companyDao()
    private val llmAuditDao = db.llmAuditDao()

    /**
     * 判定该岗位是否可以投递（既未沟通过，公司也不在黑名单）
     */
    suspend fun isJobEligible(jobId: String, companyName: String): Boolean = withContext(Dispatchers.IO) {
        if (jobDao.isJobCommunicated(jobId)) {
            return@withContext false
        }
        if (companyDao.isBlacklisted(companyName)) {
            return@withContext false
        }
        true
    }

    suspend fun recordDiscoveredJob(job: JobEntity): Boolean = withContext(Dispatchers.IO) {
        val rowId = jobDao.insertOrIgnore(job)
        rowId > 0
    }

    suspend fun markJobFilteredOut(jobId: String, reason: String) = withContext(Dispatchers.IO) {
        jobDao.updateStatus(jobId, JobEntity.STATUS_FILTERED_OUT)
    }

    suspend fun saveEvaluationResult(
        jobId: String,
        score: Int,
        reason: String,
        suggestedGreeting: String,
        status: String
    ) = withContext(Dispatchers.IO) {
        jobDao.updateEvaluationResult(
            jobId = jobId,
            score = score,
            reason = reason,
            greeting = suggestedGreeting,
            status = status
        )
    }

    suspend fun markJobCommunicated(jobId: String) = withContext(Dispatchers.IO) {
        jobDao.updateStatus(jobId, JobEntity.STATUS_COMMUNICATED)
    }

    // 🌟【新增】：将岗位标记为已完成投递流转
    suspend fun markJobDelivered(jobId: String) = withContext(Dispatchers.IO) {
        jobDao.updateStatus(jobId, JobEntity.STATUS_DELIVERED)
    }

    suspend fun getTodayCommunicatedCount(): Int = withContext(Dispatchers.IO) {
        val (start, end) = getTodayTimeRange()
        jobDao.getCommunicatedCountBetween(start, end)
    }

    suspend fun canGreetMoreToday(maxDailyLimit: Int): Boolean = withContext(Dispatchers.IO) {
        getTodayCommunicatedCount() < maxDailyLimit
    }

    suspend fun isCompanyBlacklisted(companyName: String): Boolean = withContext(Dispatchers.IO) {
        companyDao.isBlacklisted(companyName)
    }

    suspend fun addCompanyToBlacklist(companyName: String, reason: String) = withContext(Dispatchers.IO) {
        companyDao.insertOrUpdate(
            CompanyEntity(
                companyName = companyName,
                isBlacklisted = true,
                blacklistReason = reason
            )
        )
    }

    suspend fun recordLLMAudit(
        scene: String,
        jobId: String,
        modelName: String,
        prompt: String,
        response: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        durationMs: Long,
        isSuccess: Boolean,
        errorMessage: String = ""
    ) = withContext(Dispatchers.IO) {
        llmAuditDao.insertLog(
            LLMAuditLogEntity(
                scene = scene,
                jobId = jobId,
                modelName = modelName,
                promptSnapshot = prompt,
                responseRaw = response,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                durationMs = durationMs,
                isSuccess = isSuccess,
                errorMessage = errorMessage
            )
        )
    }

    suspend fun getTodayTokenCost(): Int = withContext(Dispatchers.IO) {
        val (start, end) = getTodayTimeRange()
        llmAuditDao.getTodayTotalTokens(start, end)
    }

    private fun getTodayTimeRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.timeInMillis
        return Pair(start, end)
    }
}
