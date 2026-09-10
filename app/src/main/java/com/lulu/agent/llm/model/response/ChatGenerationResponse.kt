package com.lulu.agent.llm.model.response

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek 生成定制破冰开场白的结构化输出模型
 */
data class ChatGenerationResponse(
    @SerializedName("greeting_text")
    val greetingText: String = "",

    @SerializedName("key_selling_point")
    val keySellingPoint: String = ""
) {
    /**
     * 清理并获取合规问候语（去除前后多余引号或空白）
     */
    fun getCleanGreeting(): String {
        return greetingText
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("“", "”")
    }

    fun isValid(): Boolean = getCleanGreeting().isNotBlank()
}
