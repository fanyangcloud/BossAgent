package com.agent.boss.agent.scout

/**
 * 岗位描述 (JD) 文本清洗与特征提取器
 */
object JDContentCleaner {

    // 平台垃圾声明与干扰噪声
    private val noiseDisclaimers = listOf(
        "BOSS直聘严厉打击虚假招聘",
        "如遇招聘方以任何名义索要财物",
        "防骗指南",
        "根据国家相关法律法规",
        "联系我时请说明是在BOSS直聘上看到的",
        "求职谨防诈骗"
    )

    /**
     * 洗净纯文本 JD
     */
    fun clean(rawJd: String?): String {
        if (rawJd.isNullOrBlank()) return ""

        // 显式声明为非空 String 类型，彻底解决 Smart Cast 失败的问题
        var cleaned: String = rawJd

        // 1. 过滤垃圾声明（直接按普通字符串字面量替换，更安全高效，无需转为 Regex）
        for (noise in noiseDisclaimers) {
            cleaned = cleaned.replace(noise, "")
        }

        // 2. 规范化空白行与换行符（最多保留两个连续换行）
        cleaned = cleaned.replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("[ \t]+".toRegex(), " ")
            .replace("\n{3,}".toRegex(), "\n\n")
            .trim()

        return cleaned
    }

    /**
     * 智能提取“岗位职责”与“任职资格”分段
     */
    fun extractSections(cleanedJd: String): Pair<String, String> {
        val lines = cleanedJd.lines()
        val responsibilities = StringBuilder()
        val requirements = StringBuilder()

        var currentSection = 0 // 0: 未知, 1: 职责, 2: 要求

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("岗位职责") || trimmed.contains("工作职责") || trimmed.contains("职责描述")) {
                currentSection = 1
                continue
            }
            if (trimmed.contains("任职要求") || trimmed.contains("任职资格") || trimmed.contains("岗位要求") || trimmed.contains("招聘条件")) {
                currentSection = 2
                continue
            }

            when (currentSection) {
                1 -> responsibilities.append(trimmed).append("\n")
                2 -> requirements.append(trimmed).append("\n")
                else -> {
                    // 未分段前默认按普通上下文对待
                    responsibilities.append(trimmed).append("\n")
                }
            }
        }

        return Pair(responsibilities.toString().trim(), requirements.toString().trim())
    }
}