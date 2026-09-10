package com.lulu.agent.llm.model.request

import com.google.gson.annotations.SerializedName

/**
 * DeepSeek (OpenAI兼容) 对话补全请求体
 */
data class DeepSeekChatRequest(
    @SerializedName("model")
    val model: String = MODEL_CHAT,

    @SerializedName("messages")
    val messages: List<ChatMessage>,

    @SerializedName("temperature")
    val temperature: Double = 0.3,

    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = null,

    @SerializedName("stream")
    val stream: Boolean = false
) {
    data class ChatMessage(
        @SerializedName("role")
        val role: String,

        @SerializedName("content")
        val content: String
    ) {
        companion object {
            const val ROLE_SYSTEM = "system"
            const val ROLE_USER = "user"
            const val ROLE_ASSISTANT = "assistant"

            fun system(content: String) = ChatMessage(ROLE_SYSTEM, content)
            fun user(content: String) = ChatMessage(ROLE_USER, content)
            fun assistant(content: String) = ChatMessage(ROLE_ASSISTANT, content)
        }
    }

    data class ResponseFormat(
        @SerializedName("type")
        val type: String = "json_object"
    )

    companion object {
        const val MODEL_CHAT = "deepseek-chat"
        const val MODEL_REASONER = "deepseek-reasoner"

        /**
         * 构建强制输出 JSON 对象的单轮问答请求
         */
        fun buildJsonRequest(
            systemPrompt: String,
            userPrompt: String,
            model: String = MODEL_CHAT,
            temperature: Double = 0.2
        ): DeepSeekChatRequest {
            return DeepSeekChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage.system(systemPrompt),
                    ChatMessage.user(userPrompt)
                ),
                temperature = temperature,
                responseFormat = ResponseFormat("json_object")
            )
        }
    }
}
