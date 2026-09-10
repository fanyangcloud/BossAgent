package com.lulu.agent.agent.evaluator

import com.lulu.agent.accessibility.model.ScrapedRawJob
import com.lulu.agent.llm.model.response.JDEvalResponse

/**
 * 岗位评估最终决策封装
 */
data class EvaluatorResult(
    val jobId: String,
    val title: String,
    val companyName: String,
    val isApproved: Boolean,
    val matchScore: Int = 0,
    val isLocalRejected: Boolean = false,
    val rejectReason: String = "",
    val highlights: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val summaryReason: String = ""
) {
    companion object {
        /**
         * 本地 0-Token 规则直接淘汰
         */
        fun fromLocalReject(job: ScrapedRawJob, reason: String): EvaluatorResult {
            return EvaluatorResult(
                jobId = job.resolveJobId(),
                title = job.title,
                companyName = job.companyName,
                isApproved = false,
                matchScore = 0,
                isLocalRejected = true,
                rejectReason = reason,
                summaryReason = "命中本地过滤规则: $reason"
            )
        }

        /**
         * DeepSeek 深度评估成功
         */
        fun fromLLMResponse(
            job: ScrapedRawJob,
            response: JDEvalResponse,
            scoreThreshold: Int = 70
        ): EvaluatorResult {
            val approved = response.isApproved(scoreThreshold)
            return EvaluatorResult(
                jobId = job.resolveJobId(),
                title = job.title,
                companyName = job.companyName,
                isApproved = approved,
                matchScore = response.matchScore,
                isLocalRejected = false,
                rejectReason = if (!approved) response.summaryReason else "",
                highlights = response.highlights,
                risks = response.risks,
                summaryReason = response.summaryReason
            )
        }

        /**
         * 网络或解析异常兜底
         */
        fun fromError(job: ScrapedRawJob, errorMsg: String): EvaluatorResult {
            return EvaluatorResult(
                jobId = job.resolveJobId(),
                title = job.title,
                companyName = job.companyName,
                isApproved = false,
                matchScore = 0,
                isLocalRejected = false,
                rejectReason = errorMsg,
                summaryReason = "大模型评估异常: $errorMsg"
            )
        }
    }
}
