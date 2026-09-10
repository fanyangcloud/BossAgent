package com.lulu.agent.accessibility.model

import com.lulu.agent.data.local.entity.JobEntity
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * 无障碍采集到的原始岗位卡片数据
 */
data class ScrapedRawJob(
    val jobId: String = "",
    val title: String = "",
    val companyName: String = "",
    val salaryText: String = "",
    val city: String = "",
    val hrName: String = "",
    val hrTitle: String = "",
    val hrActiveStatus: String = "",
    val jobDescription: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {

    /**
     * 自动生成 JobId（若从 UI 抓取不到平台的真实 JobId，则通过 标题+公司名+薪资 计算 MD5 指纹兜底）
     */
    fun resolveJobId(): String {
        if (jobId.isNotBlank()) return jobId
        val rawKey = "${title.trim()}_${companyName.trim()}_${salaryText.trim()}"
        return md5(rawKey)
    }

    /**
     * 校验抓取到的信息是否包含最低限度的核心要素
     */
    fun isValid(): Boolean {
        return title.isNotBlank() && companyName.isNotBlank()
    }

    /**
     * 解析薪资下限 (例如: "25-35K·14薪" -> 25)
     */
    fun parseSalaryMin(): Int {
        val matcher = SALARY_PATTERN.matcher(salaryText)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull() ?: 0
        }
        return 0
    }

    /**
     * 解析薪资上限 (例如: "25-35K·14薪" -> 35)
     */
    fun parseSalaryMax(): Int {
        val matcher = SALARY_PATTERN.matcher(salaryText)
        if (matcher.find()) {
            return matcher.group(2)?.toIntOrNull() ?: 0
        }
        return 0
    }

    /**
     * 转为数据库持久化模型 JobEntity
     */
    fun toJobEntity(status: String = JobEntity.STATUS_DISCOVERED): JobEntity {
        return JobEntity(
            jobId = resolveJobId(),
            title = title.trim(),
            companyName = companyName.trim(),
            salaryText = salaryText.trim(),
            salaryMin = parseSalaryMin(),
            salaryMax = parseSalaryMax(),
            city = city.trim(),
            hrName = hrName.trim(),
            hrTitle = hrTitle.trim(),
            hrActiveStatus = hrActiveStatus.trim(),
            jobDescription = jobDescription.trim(),
            status = status,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }

    companion object {
        // 匹配常见格式：20-40K、15-25k、8-13K·13薪
        private val SALARY_PATTERN = Pattern.compile("(\\d+)[-~](\\d+)[kK]")

        private fun md5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
