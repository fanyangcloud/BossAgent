package com.agent.boss.llm.limiter

import android.util.Log
import com.agent.boss.data.repository.JobRepository
import java.util.concurrent.atomic.AtomicInteger

/**
 * DeepSeek Token 消耗与每日消费预算熔断器
 */
class TokenUsageTracker(
    private val jobRepository: JobRepository,
    private val maxDailyCostRmb: Double = 2.0 // 默认单日大模型预算上限：2.00 元
) {

    private val tag = "TokenUsageTracker"

    // 当日内存增量计数器（防止高并发下频繁查库）
    private val todayPromptTokens = AtomicInteger(0)
    private val todayCompletionTokens = AtomicInteger(0)

    companion object {
        // DeepSeek 定价参考 (元 / 100万 tokens)
        // 输入 Prompt (未命中缓存约 1.0 元/M，命中约 0.2 元/M，此处取保守均值 1.0 元)
        private const val COST_PER_MILLION_PROMPT = 1.0
        // 输出 Completion (约 2.0 元/M)
        private const val COST_PER_MILLION_COMPLETION = 2.0
    }

    /**
     * 累加单次请求消耗
     */
    fun recordTokens(promptTokens: Int, completionTokens: Int) {
        todayPromptTokens.addAndGet(promptTokens)
        todayCompletionTokens.addAndGet(completionTokens)
        Log.d(tag, "已记录 Token: Prompt+$promptTokens, Completion+$completionTokens | 今日预估总花费: ￥${"%.4f".format(getTodayEstimatedCostRmb())}")
    }

    /**
     * 计算今日消耗总金额 (元)
     */
    fun getTodayEstimatedCostRmb(): Double {
        val promptCost = (todayPromptTokens.get().toDouble() / 1_000_000.0) * COST_PER_MILLION_PROMPT
        val completionCost = (todayCompletionTokens.get().toDouble() / 1_000_000.0) * COST_PER_MILLION_COMPLETION
        return promptCost + completionCost
    }

    /**
     * 检查是否超出每日消费上限（熔断判定）
     */
    suspend fun isBudgetExceeded(): Boolean {
        // 同步数据库累计的历史消耗（针对冷启动或跨天场景）
        val dbTotalTokens = jobRepository.getTodayTokenCost()
        val currentCost = getTodayEstimatedCostRmb()

        // 双重校验：内存估值或数据库累计折算
        val dbEstimatedCost = (dbTotalTokens.toDouble() / 1_000_000.0) * 1.5 // 混合均价约 1.5元/M
        val actualMaxCost = maxOf(currentCost, dbEstimatedCost)

        if (actualMaxCost >= maxDailyCostRmb) {
            Log.e(tag, "🚨 触发预算熔断！今日已消耗 ￥${"%.4f".format(actualMaxCost)}，超过设定阈值 ￥$maxDailyCostRmb")
            return true
        }
        return false
    }
}
