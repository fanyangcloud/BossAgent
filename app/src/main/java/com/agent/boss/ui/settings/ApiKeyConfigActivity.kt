package com.agent.boss.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.agent.boss.data.local.AppDatabase
import com.agent.boss.data.pref.AppSettings
import com.agent.boss.data.pref.EncryptedDataStore
import com.agent.boss.data.repository.ConfigRepository
import com.agent.boss.data.repository.JobRepository
import com.agent.boss.llm.DeepSeekClient
import com.agent.boss.llm.limiter.TokenUsageTracker
import com.agent.boss.llm.prompt.PromptManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DeepSeek API Key 凭证配置与连通性诊断页面
 */
class ApiKeyConfigActivity : AppCompatActivity() {

    private lateinit var configRepository: ConfigRepository
    private lateinit var deepSeekClient: DeepSeekClient

    private lateinit var keyInput: EditText
    private lateinit var testStatusTv: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDependencies()
        buildUi()
        loadCurrentKey()
    }

    private fun initDependencies() {
        val encryptedDataStore = EncryptedDataStore.getInstance(this)
        val appSettings = AppSettings.getInstance(this)
        configRepository = ConfigRepository(encryptedDataStore, appSettings)

        val db = AppDatabase.getInstance(this)
        val jobRepo = JobRepository(db)
        val tokenTracker = TokenUsageTracker(jobRepo)
        val promptManager = PromptManager(configRepository)

        deepSeekClient = DeepSeekClient.getInstance(
            configRepository,
            jobRepo,
            tokenTracker,
            promptManager
        )
    }

    private fun buildUi() {
        title = "DeepSeek 凭证配置"

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121214"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp2px(16)
            setPadding(p, p, p, p)
        }

        // 提示卡片
        val tipCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp2px(10).toFloat()
                setColor(Color.parseColor("#1E1E24"))
                setStroke(1, Color.parseColor("#33FFFFFF"))
            }
            val cp = dp2px(12)
            setPadding(cp, cp, cp, cp)
        }

        val tipTitle = TextView(this).apply {
            text = "🔑 什么是 DeepSeek API Key？"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val tipContent = TextView(this).apply {
            text = "用于驱动 Evaluator 参谋进行智能评分与 Communicator 生成定制破冰话术。\n请前往 platform.deepseek.com 注册并创建 API Key，密钥将通过 Android Keystore AES-256 全程加密保存。"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            setLineSpacing(dp2px(2).toFloat(), 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(6) }
        }

        tipCard.addView(tipTitle)
        tipCard.addView(tipContent)
        container.addView(tipCard)

        // 输入框卡片
        val inputLabel = TextView(this).apply {
            text = "API KEY (sk-...)"
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(20) }
        }
        container.addView(inputLabel)

        keyInput = EditText(this).apply {
            hint = "请输入 sk- 开头的 API Key"
            setHintTextColor(Color.parseColor("#616161"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            background = GradientDrawable().apply {
                cornerRadius = dp2px(8).toFloat()
                setColor(Color.parseColor("#1E1E24"))
                setStroke(1, Color.parseColor("#424242"))
            }
            val ep = dp2px(12)
            setPadding(ep, ep, ep, ep)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(6) }
        }
        container.addView(keyInput)

        // 测试状态指示与进度条
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp2px(24), dp2px(24)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp2px(12)
            }
        }
        container.addView(progressBar)

        testStatusTv = TextView(this).apply {
            text = "状态: 未测试"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(8) }
        }
        container.addView(testStatusTv)

        // 按钮栏
        val pingBtn = TextView(this).apply {
            text = "⚡ 一键连通性测试 (Ping)"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp2px(8).toFloat()
                setColor(Color.parseColor("#2E7D32"))
            }
            val bp = dp2px(12)
            setPadding(bp, bp, bp, bp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(16) }
            setOnClickListener { performPingTest() }
        }
        container.addView(pingBtn)

        val saveBtn = TextView(this).apply {
            text = "💾 保存配置"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp2px(8).toFloat()
                setColor(Color.parseColor("#1565C0"))
            }
            val bp = dp2px(12)
            setPadding(bp, bp, bp, bp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(12) }
            setOnClickListener { saveKey() }
        }
        container.addView(saveBtn)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    private fun loadCurrentKey() {
        val currentKey = configRepository.getDeepSeekApiKey()
        if (currentKey.isNotEmpty()) {
            keyInput.setText(currentKey)
            testStatusTv.text = "状态: 已保存凭证 (${currentKey.take(6)}...${currentKey.takeLast(4)})"
        }
    }

    private fun performPingTest() {
        val inputKey = keyInput.text.toString().trim()
        if (inputKey.isBlank()) {
            Toast.makeText(this, "请先输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        testStatusTv.text = "正在握手官方 API 节点，请稍候..."
        testStatusTv.setTextColor(Color.parseColor("#FFA726"))

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                deepSeekClient.testConnection(inputKey)
            }
            progressBar.visibility = View.GONE
            result.fold(
                onSuccess = {
                    testStatusTv.text = "✅ 连通成功！DeepSeek 响应正常"
                    testStatusTv.setTextColor(Color.parseColor("#4CAF50"))
                    Toast.makeText(this@ApiKeyConfigActivity, "连通测试通过！", Toast.LENGTH_SHORT).show()
                },
                onFailure = { err ->
                    testStatusTv.text = "❌ 连通失败: ${err.message}"
                    testStatusTv.setTextColor(Color.parseColor("#E53935"))
                }
            )
        }
    }

    private fun saveKey() {
        val inputKey = keyInput.text.toString().trim()
        configRepository.setDeepSeekApiKey(inputKey)
        Toast.makeText(this, "API Key 已安全加密保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
