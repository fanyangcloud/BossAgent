package com.agent.boss.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.agent.boss.data.pref.AppSettings
import com.agent.boss.data.pref.EncryptedDataStore
import com.agent.boss.data.repository.ConfigRepository

/**
 * 求职者核心简历与背景知识库编辑器 (Markdown 格式)
 */
class ResumeEditorActivity : AppCompatActivity() {

    private lateinit var configRepository: ConfigRepository
    private lateinit var resumeEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configRepository = ConfigRepository(
            EncryptedDataStore.getInstance(this),
            AppSettings.getInstance(this)
        )
        buildUi()
        loadResume()
    }

    private fun buildUi() {
        title = "个人简历与背景设定"

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121214"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp2px(16)
            setPadding(p, p, p, p)
        }

        // 顶部功能提示
        val descTv = TextView(this).apply {
            text = "📝 请填入你的个人简历（建议 Markdown 格式）。DeepSeek 会根据你的真实技能栈与项目成果对岗位进行契合度打分，并构思针对性的破冰开场白。"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            setLineSpacing(dp2px(2).toFloat(), 1.0f)
        }
        container.addView(descTv)

        // 辅助操作按钮栏
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(12) }
        }

        val templateBtn = TextView(this).apply {
            text = "📋 填入参考模板"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp2px(6).toFloat()
                setColor(Color.parseColor("#424242"))
            }
            val bp = dp2px(8)
            setPadding(bp, bp, bp, bp)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp2px(8)
            }
            setOnClickListener { insertTemplate() }
        }

        val saveBtn = TextView(this).apply {
            text = "💾 保存简历"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp2px(6).toFloat()
                setColor(Color.parseColor("#1565C0"))
            }
            val bp = dp2px(8)
            setPadding(bp, bp, bp, bp)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { saveResume() }
        }

        btnRow.addView(templateBtn)
        btnRow.addView(saveBtn)
        container.addView(btnRow)

        // 编辑区
        resumeEdit = EditText(this).apply {
            hint = "# 个人简历\n\n## 技术栈\n- 精通 Android Framework 与 Jetpack...\n\n## 工作经历\n..."
            setHintTextColor(Color.parseColor("#616161"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            gravity = Gravity.TOP or Gravity.START
            minLines = 18
            background = GradientDrawable().apply {
                cornerRadius = dp2px(8).toFloat()
                setColor(Color.parseColor("#1E1E24"))
                setStroke(1, Color.parseColor("#33FFFFFF"))
            }
            val ep = dp2px(12)
            setPadding(ep, ep, ep, ep)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(12) }
        }
        container.addView(resumeEdit)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    private fun loadResume() {
        val currentResume = configRepository.getResumeMarkdown()
        if (currentResume.isNotBlank()) {
            resumeEdit.setText(currentResume)
        }
    }

    private fun insertTemplate() {
        val template = """# 资深 Android 开发工程师

## 基本信息
- 经验: 6年 | 学历: 本科 | 意向城市: 远程 / 全国
- 期望薪资: 25K - 40K

## 核心专业优势
1. 深入掌握 Kotlin 协程/Flow 异步响应式编程，精通 Jetpack 架构组件与 MVVM/MVI 架构。
2. 具备大型 App 性能调优经验（启动优化、内存抖动与 OOM 治理、卡顿监控、APK 瘦身）。
3. 熟悉 Android Framework 核心机制（Handler、Binder IPC、WMS/AMS、View 绘制流程）。
4. 拥有无障碍服务 (AccessibilityService) 深度开发经验，擅长拟人化手势仿真与节点安全生命周期管理。

## 代表项目经历
- **大型电商/社交 App 核心架构演进**：主导模块化与组件化拆分，启动时间降低 35%，线上崩溃率控制在 0.05% 以下。
- **端侧 AI 与自动化智能体研发**：主导 LLM 与客户端协同架构，实现端侧自主视觉感知与语义推演。"""
        resumeEdit.setText(template.trimIndent())
        Toast.makeText(this, "已载入标准简历模板", Toast.LENGTH_SHORT).show()
    }

    private fun saveResume() {
        val text = resumeEdit.text.toString().trim()
        configRepository.setResumeMarkdown(text)
        Toast.makeText(this, "简历已安全加密更新", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
