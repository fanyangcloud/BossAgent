package com.lulu.agent.llm.filter

import android.util.Log
import com.lulu.agent.accessibility.model.ScrapedRawJob
import com.lulu.agent.data.repository.ConfigRepository
import com.lulu.agent.data.repository.JobRepository

/**
 * 0-Token 本地快速过滤网关
 */
class LocalPreFilter(
    private val jobRepository: JobRepository,
    private val configRepository: ConfigRepository
) {

    private val tag = "LocalPreFilter"

    sealed class FilterResult {
        object Pass : FilterResult()
        data class Reject(val reason: String) : FilterResult()
    }

    // 常见头部外包与劳务派遣黑名单关键词
    private val defaultOutsourcingKeywords = listOf(
        "外包", "劳务派遣", "人力外派", "驻场", "外派", "外服",
        "中软国际", "软通动力", "德科", "博彦科技", "法本信息",
        "诚迈科技", "文思海辉", "信华信", "拓保", "易宝软件", "新致软件"
    )

    /**
     * 本地规则综合流水线
     */
    suspend fun check(job: ScrapedRawJob): FilterResult {
        val jobId = job.resolveJobId()
        val company = job.companyName
        val title = job.title
        val jd = job.jobDescription

        // 1. 数据库去重与黑名单排查
        if (!jobRepository.isJobEligible(jobId, company)) {
            Log.d(tag, "拦截：公司 [$company] 位于黑名单或职位已沟通过")
            return FilterResult.Reject("公司命中黑名单或该岗位已沟通过")
        }

        // 2. 外包公司与关键词过滤
        if (configRepository.isFilterOutsourcing()) {
            val matchedKeyword = findOutsourcingMatch("$company $title $jd")
            if (matchedKeyword != null) {
                Log.d(tag, "拦截：命中外包特征 [$matchedKeyword]")
                return FilterResult.Reject("命中外包排查关键词: $matchedKeyword")
            }
        }

        // 3. 期望薪资下限校验
        val minSalaryK = configRepository.getMinSalaryFilterK()
        if (minSalaryK > 0) {
            val maxSalary = job.parseSalaryMax()
            // 如果岗位标明的薪资上限比求职者的最低要求还要低，直接放弃
            if (maxSalary in 1 until minSalaryK) {
                Log.d(tag, "拦截：薪资上限 ${maxSalary}K 低于期望下限 ${minSalaryK}K")
                return FilterResult.Reject("薪水上限(${maxSalary}K)低于期望下限(${minSalaryK}K)")
            }
        }

        // 4. HR 长期失联/僵尸岗位过滤
        val hrStatus = job.hrActiveStatus
        if (hrStatus.contains("数月前") || hrStatus.contains("半年前") || hrStatus.contains("年前活跃")) {
            Log.d(tag, "拦截：HR活跃度过低 [$hrStatus]")
            return FilterResult.Reject("HR活跃度过低: $hrStatus")
        }

        return FilterResult.Pass
    }

    private fun findOutsourcingMatch(content: String): String? {
        for (kw in defaultOutsourcingKeywords) {
            if (content.contains(kw)) {
                return kw
            }
        }
        return null
    }
}
