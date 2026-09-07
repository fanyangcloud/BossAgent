package com.agent.boss.agent.communicator

/**
 * HR / 招聘者回复意图识别引擎
 */
object IntentClassifier {

    enum class RecruiterIntent(val title: String) {
        ASK_RESUME("索要简历"),
        SCHEDULE_INTERVIEW("约谈沟通/面试"),
        SALARY_INQUIRY("询问薪水期望"),
        REJECT("婉拒/暂不匹配"),
        NORMAL_GREETING("常规应答"),
        UNKNOWN("未知意图")
    }

    data class ClassifyResult(
        val intent: RecruiterIntent,
        val matchedKeyword: String,
        val actionSuggestion: String
    )

    private val askResumeKeywords = listOf(
        "发一份简历", "发下简历", "发我简历", "简历发一下", "发个简历",
        "发邮箱", "邮箱发我", "附件简历", "发送在线简历", "看下简历"
    )

    private val interviewKeywords = listOf(
        "什么时候方便面试", "约个时间面试", "方便电话聊聊", "来公司聊聊",
        "加个微信详聊", "方便来线下面试", "腾讯会议", "电话沟通一下"
    )

    private val salaryKeywords = listOf(
        "期望薪资是多少", "期望薪资", "目前薪水", "目前薪资"
    )

    private val rejectKeywords = listOf(
        "暂不匹配", "不太合适", "暂时不合适", "岗位已招满",
        "感谢关注", "学历暂不符合", "经验不太匹配"
    )

    private val greetingKeywords = listOf(
        "您好", "在的", "你好", "好的"
    )

    /**
     * 对招聘者的单条或复合回复进行意图归类
     */
    fun classify(replyText: String?): ClassifyResult {
        if (replyText.isNullOrBlank()) {
            return ClassifyResult(RecruiterIntent.UNKNOWN, "", "无有效文本")
        }

        val text = replyText.trim()

        // 1. 优先判定索要简历（高频转化路径）
        for (kw in askResumeKeywords) {
            if (text.contains(kw)) {
                return ClassifyResult(RecruiterIntent.ASK_RESUME, kw, "对方索要简历，可触发附件简历发送")
            }
        }

        // 2. 判定约谈面试
        for (kw in interviewKeywords) {
            if (text.contains(kw)) {
                return ClassifyResult(RecruiterIntent.SCHEDULE_INTERVIEW, kw, "对方发起面试邀请，建议人工介入确认时间")
            }
        }

        // 3. 判定婉拒
        for (kw in rejectKeywords) {
            if (text.contains(kw)) {
                return ClassifyResult(RecruiterIntent.REJECT, kw, "对方已婉拒，标记归档避免重复打扰")
            }
        }

        // 4. 判定薪资问询
        for (kw in salaryKeywords) {
            if (text.contains(kw)) {
                return ClassifyResult(RecruiterIntent.SALARY_INQUIRY, kw, "对方打听期望薪资")
            }
        }

        // 5. 常规招呼
        for (kw in greetingKeywords) {
            if (text.contains(kw)) {
                return ClassifyResult(RecruiterIntent.NORMAL_GREETING, kw, "常规客套回复")
            }
        }

        return ClassifyResult(RecruiterIntent.UNKNOWN, "", "暂无明确意图特征")
    }
}
