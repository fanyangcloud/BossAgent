package com.lulu.agent.llm

import android.util.Log
import com.lulu.agent.accessibility.model.ScrapedRawJob
import com.lulu.agent.data.local.entity.LLMAuditLogEntity
import com.lulu.agent.data.repository.ConfigRepository
import com.lulu.agent.data.repository.JobRepository
import com.lulu.agent.llm.api.DeepSeekApiService
import com.lulu.agent.llm.api.DeepSeekChatResponse
import com.lulu.agent.llm.limiter.DeduplicationCache
import com.lulu.agent.llm.limiter.TokenUsageTracker
import com.lulu.agent.llm.model.request.DeepSeekChatRequest
import com.lulu.agent.llm.model.response.ChatGenerationResponse
import com.lulu.agent.llm.model.response.JDEvalResponse
import com.lulu.agent.llm.prompt.PromptManager
import com.google.gson.Gson
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 大模型网络门面单例
 *
 * 核心指标：
 * - 60s 完整超时门限
 * - 最多 3 次异常重试与指数退避
 * - 纯非流式结构化输出
 */
class DeepSeekClient(
    private val configRepository: ConfigRepository,
    private val jobRepository: JobRepository,
    private val tokenUsageTracker: TokenUsageTracker,
    private val promptManager: PromptManager
) {

    private val tag = "DeepSeekClient"
    private val gson = Gson()

    private val apiService: DeepSeekApiService by lazy {
        buildRetrofit().create(DeepSeekApiService::class.java)
    }

    companion object {
        private const val BASE_URL = "https://api.deepseek.com/"
        private const val TIMEOUT_SECONDS = 60L
        private const val MAX_RETRY_COUNT = 3

        @Volatile
        private var instance: DeepSeekClient? = null

        fun getInstance(
            configRepository: ConfigRepository,
            jobRepository: JobRepository,
            tokenUsageTracker: TokenUsageTracker,
            promptManager: PromptManager
        ): DeepSeekClient {
            return instance ?: synchronized(this) {
                instance ?: DeepSeekClient(
                    configRepository,
                    jobRepository,
                    tokenUsageTracker,
                    promptManager
                ).also { instance = it }
            }
        }
    }

    // ==================== 核心业务暴露 API ====================

    /**
     * 评估岗位契合度（包含缓存拦截、预算校验、3次重试与审计日志）
     */
    suspend fun evaluateJob(rawJob: ScrapedRawJob): Result<JDEvalResponse> {
        val jdText = rawJob.jobDescription

        // 1. 指纹缓存前置拦截 (0 Token 消耗)
        val cached = DeduplicationCache.getCachedEvaluation(jdText)
        if (cached != null) {
            Log.i(tag, "🎯 命中内存指纹缓存，复用前序评估结果: ${cached.matchScore}分")
            return Result.success(cached)
        }

        // 2. 每日预算熔断校验
        if (tokenUsageTracker.isBudgetExceeded()) {
            val msg = "今日 DeepSeek 预算已超出限额，自动化熔断保护"
            Log.e(tag, msg)
            return Result.failure(IllegalStateException(msg))
        }

        // 3. 构建请求体（强制非流式与 JSON 输出）
        val request = promptManager.buildEvaluationRequest(rawJob).copy(stream = false)
        val startTime = System.currentTimeMillis()

        // 4. 执行 3 次重试网络调用
        val networkResult = executeWithRetry(MAX_RETRY_COUNT) { attempt ->
            Log.d(tag, "发起 JD 契合度评估 (第 $attempt 次尝试)...")
            val authHeader = "Bearer ${configRepository.getDeepSeekApiKey()}"
            apiService.createChatCompletion(authHeader, request)
        }

        val duration = System.currentTimeMillis() - startTime

        return networkResult.fold(
            onSuccess = { response ->
                val rawJson = response.choices?.firstOrNull()?.message?.content ?: ""
                val promptTokens = response.usage?.promptTokens ?: 0
                val completionTokens = response.usage?.completionTokens ?: 0
                val totalTokens = response.usage?.totalTokens ?: 0

                try {
                    val cleanJson = cleanMarkdownWrappers(rawJson)
                    val evalResponse = gson.fromJson(cleanJson, JDEvalResponse::class.java)

                    // 写入指纹缓存
                    DeduplicationCache.putEvaluation(jdText, evalResponse)

                    // 记录 Token 与数据库审计流水
                    tokenUsageTracker.recordTokens(promptTokens, completionTokens)
                    jobRepository.recordLLMAudit(
                        scene = LLMAuditLogEntity.SCENE_EVALUATE,
                        jobId = rawJob.resolveJobId(),
                        modelName = request.model,
                        prompt = request.messages.lastOrNull()?.content ?: "",
                        response = rawJson,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        durationMs = duration,
                        isSuccess = true
                    )

                    Log.i(tag, "✅ 岗位评估成功 | 评分: ${evalResponse.matchScore} | 耗时: ${duration}ms")
                    Result.success(evalResponse)
                } catch (e: Exception) {
                    val parseErr = "JSON 反序列化失败: ${e.message} | 原始返回: $rawJson"
                    Log.e(tag, parseErr)
                    recordFailedAudit(rawJob.resolveJobId(), request, rawJson, duration, parseErr)
                    Result.failure(e)
                }
            },
            onFailure = { error ->
                Log.e(tag, "❌ 评估网络请求重试耗尽失败: ${error.message}")
                recordFailedAudit(rawJob.resolveJobId(), request, "", duration, error.message ?: "未知网络异常")
                Result.failure(error)
            }
        )
    }

    /**
     * 生成定制破冰问候语（同样配置 60s 超时与 3 次重试）
     */
    suspend fun generateGreeting(
        rawJob: ScrapedRawJob,
        highlights: List<String>
    ): Result<ChatGenerationResponse> {
        if (tokenUsageTracker.isBudgetExceeded()) {
            return Result.failure(IllegalStateException("今日大模型预算超标，已终止话术生成"))
        }

        val request = promptManager.buildGreetingRequest(rawJob, highlights).copy(stream = false)
        val startTime = System.currentTimeMillis()

        val networkResult = executeWithRetry(MAX_RETRY_COUNT) { attempt ->
            Log.d(tag, "生成破冰话术 (第 $attempt 次尝试)...")
            val authHeader = "Bearer ${configRepository.getDeepSeekApiKey()}"
            apiService.createChatCompletion(authHeader, request)
        }

        val duration = System.currentTimeMillis() - startTime

        return networkResult.fold(
            onSuccess = { response ->
                val rawJson = response.choices?.firstOrNull()?.message?.content ?: ""
                val promptTokens = response.usage?.promptTokens ?: 0
                val completionTokens = response.usage?.completionTokens ?: 0
                val totalTokens = response.usage?.totalTokens ?: 0

                try {
                    val cleanJson = cleanMarkdownWrappers(rawJson)
                    val chatResponse = gson.fromJson(cleanJson, ChatGenerationResponse::class.java)

                    tokenUsageTracker.recordTokens(promptTokens, completionTokens)
                    jobRepository.recordLLMAudit(
                        scene = LLMAuditLogEntity.SCENE_GREETING,
                        jobId = rawJob.resolveJobId(),
                        modelName = request.model,
                        prompt = request.messages.lastOrNull()?.content ?: "",
                        response = rawJson,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        durationMs = duration,
                        isSuccess = true
                    )

                    Log.i(tag, "✅ 问候语生成成功: ${chatResponse.greetingText}")
                    Result.success(chatResponse)
                } catch (e: Exception) {
                    val parseErr = "问候语 JSON 解析失败: ${e.message}"
                    Log.e(tag, parseErr)
                    recordFailedAudit(rawJob.resolveJobId(), request, rawJson, duration, parseErr)
                    Result.failure(e)
                }
            },
            onFailure = { error ->
                recordFailedAudit(rawJob.resolveJobId(), request, "", duration, error.message ?: "网络异常")
                Result.failure(error)
            }
        )
    }

    /**
     * 设置页面连通性测试
     */
    suspend fun testConnection(customApiKey: String): Result<Boolean> {
        val testRequest = DeepSeekChatRequest.buildJsonRequest(
            systemPrompt = "你是一个联通性测试助手，必须输出合法JSON: {\"status\": \"ok\"}",
            userPrompt = "ping"
        ).copy(stream = false)

        return try {
            val response = apiService.createChatCompletion("Bearer $customApiKey", testRequest)
            if (response.isSuccessful && response.body()?.choices?.isNotEmpty() == true) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("HTTP 状态异常: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 核心重试与网络装配底座 ====================

    /**
     * 具备指数退避的挂起重试执行器 (最多重试 maxRetries 次)
     */
    private suspend fun <T> executeWithRetry(
        maxRetries: Int,
        block: suspend (attempt: Int) -> retrofit2.Response<T>
    ): Result<T> {
        var currentAttempt = 1
        var lastException: Throwable? = null

        while (currentAttempt <= maxRetries) {
            try {
                val response = block(currentAttempt)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        return Result.success(body)
                    }
                }

                // 遇到 HTTP 错误 (429 限流 / 5xx 服务端超载)
                val errorCode = response.code()
                val errorMsg = response.errorBody()?.string() ?: "HTTP $errorCode"
                lastException = IllegalStateException("请求失败 [$errorCode]: $errorMsg")
                Log.w(tag, "请求未成功 (第 $currentAttempt 次): $errorMsg")
            } catch (e: Exception) {
                lastException = e
                Log.w(tag, "网络或超时异常 (第 $currentAttempt 次): ${e.message}")
            }

            if (currentAttempt < maxRetries) {
                // 指数退避等待: 1000ms, 2000ms, 4000ms...
                val backoffDelay = (1000L * (1L shl (currentAttempt - 1)))
                Log.d(tag, "将在 ${backoffDelay}ms 后重试...")
                delay(backoffDelay)
            }
            currentAttempt++
        }

        return Result.failure(lastException ?: IllegalStateException("已达最大重试次数 ($maxRetries)"))
    }

    /**
     * 清理大模型偶发的 ```json ``` 标记
     */
    private fun cleanMarkdownWrappers(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json")
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```")
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```")
        }
        return text.trim()
    }

    private suspend fun recordFailedAudit(
        jobId: String,
        request: DeepSeekChatRequest,
        rawResponse: String,
        durationMs: Long,
        error: String
    ) {
        jobRepository.recordLLMAudit(
            scene = "API_REQUEST_FAILED",
            jobId = jobId,
            modelName = request.model,
            prompt = request.messages.lastOrNull()?.content ?: "",
            response = rawResponse,
            promptTokens = 0,
            completionTokens = 0,
            totalTokens = 0,
            durationMs = durationMs,
            isSuccess = false,
            errorMessage = error
        )
    }

    private fun buildRetrofit(): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // 统一注入 Authorization 拦截器
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val hasAuth = original.header("Authorization") != null
            val requestBuilder = original.newBuilder()

            if (!hasAuth) {
                val key = configRepository.getDeepSeekApiKey()
                if (key.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $key")
                }
            }
            chain.proceed(requestBuilder.build())
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
