package com.agent.boss.llm.api

import com.agent.boss.llm.model.request.DeepSeekChatRequest
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * DeepSeek 标准 API 通信接口
 */
interface DeepSeekApiService {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: DeepSeekChatRequest
    ): Response<DeepSeekChatResponse>
}

/**
 * OpenAI 格式的标准顶层响应包装体
 */
data class DeepSeekChatResponse(
    @SerializedName("id")
    val id: String?,

    @SerializedName("choices")
    val choices: List<Choice>?,

    @SerializedName("usage")
    val usage: Usage?
)

data class Choice(
    @SerializedName("index")
    val index: Int,

    @SerializedName("message")
    val message: ResponseMessage?,

    @SerializedName("finish_reason")
    val finishReason: String?
)

data class ResponseMessage(
    @SerializedName("role")
    val role: String,

    @SerializedName("content")
    val content: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,

    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,

    @SerializedName("total_tokens")
    val totalTokens: Int = 0
)
