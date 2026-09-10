package com.lulu.agent.llm.model.response

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek 评估岗位匹配度的结构化输出模型
 */
data class JDEvalResponse(
    @SerializedName("match_score")
    val matchScore: Int = 0,

    @SerializedName("decision")
    val decision: String = DECISION_REJECT,

    @SerializedName("highlights")
    val highlights: List<String> = emptyList(),

    @SerializedName("risks")
    val risks: List<String> = emptyList(),

    @SerializedName("summary_reason")
    val summaryReason: String = ""
) {
    /**
     * 综合判定：决策为 ACCEPT 且契合度分数达到门槛（默认 70 分）
     */
    fun isApproved(minScoreThreshold: Int = 70): Boolean {
        return decision.equals(DECISION_ACCEPT, ignoreCase = true) && matchScore >= minScoreThreshold
    }

    companion object {
        const val DECISION_ACCEPT = "ACCEPT"
        const val DECISION_REJECT = "REJECT"
    }
}
