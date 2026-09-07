package com.agent.boss.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.agent.boss.BossApp
import com.agent.boss.accessibility.BossAccessibilityService
import com.agent.boss.floating.FloatingHUDService
import com.agent.boss.ui.dashboard.HistoryRecordActivity
import com.agent.boss.ui.settings.ApiKeyConfigActivity
import com.agent.boss.ui.settings.ResumeEditorActivity

/**
 * 宿主应用总控大厅 (已解决自动跳转与预热等待)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var accessibilityStatusTv: TextView
    private lateinit var overlayStatusTv: TextView
    private lateinit var batteryStatusTv: TextView
    private lateinit var launchBtn: TextView

    companion object {
        private const val BOSS_PACKAGE = "com.hpbr.bosszhipin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun buildUi() {
        title = "BossAgent 控制中心"

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121214"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp2px(16)
            setPadding(p, p, p, p)
        }

        val titleTv = TextView(this).apply {
            text = "🤖 BossAgent 智能求职体"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val subTitleTv = TextView(this).apply {
            text = "基于 Android 无障碍 + DeepSeek 大模型驱动的全链路自主寻岗助理"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(4) }
        }

        container.addView(titleTv)
        container.addView(subTitleTv)

        val permSectionTitle = TextView(this).apply {
            text = "运行权限状态检查"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#B0BEC5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(20) }
        }
        container.addView(permSectionTitle)

        val permCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp2px(10).toFloat()
                setColor(Color.parseColor("#1E1E24"))
            }
            val cp = dp2px(12)
            setPadding(cp, cp, cp, cp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(8) }
        }

        accessibilityStatusTv = createPermissionRow(permCard, "1. 无障碍核心通道 (用于屏幕感知与手势)", "前往开启") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        overlayStatusTv = createPermissionRow(permCard, "2. 悬浮窗权限 (展示极客控制台与状态灯)", "前往授权") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }

        batteryStatusTv = createPermissionRow(permCard, "3. 忽略电池优化 (防止后台断流)", "加入白名单") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }

        container.addView(permCard)

        val navSectionTitle = TextView(this).apply {
            text = "智能体核心大脑配置"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#B0BEC5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(20) }
        }
        container.addView(navSectionTitle)

        val navContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(8) }
        }

        createNavCard(navContainer, "🔑 DeepSeek API 凭证管理", "配置 API Key 与一键连通性测试") {
            startActivity(Intent(this, ApiKeyConfigActivity::class.java))
        }

        createNavCard(navContainer, "📝 个人简历与技术画像", "录入 Markdown 简历，作为 LLM 评估与话术生成的基准") {
            startActivity(Intent(this, ResumeEditorActivity::class.java))
        }

        createNavCard(navContainer, "📊 岗位投递与评估历史", "查看所有分析过的岗位、DeepSeek 评分与打招呼记录") {
            startActivity(Intent(this, HistoryRecordActivity::class.java))
        }

        container.addView(navContainer)

        launchBtn = TextView(this).apply {
            text = "🚀 启动 BossAgent (直接跳转并寻岗)"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp2px(10).toFloat()
                setColor(Color.parseColor("#1565C0"))
            }
            val bp = dp2px(14)
            setPadding(bp, bp, bp, bp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(24) }
            setOnClickListener { handleLaunchAgent() }
        }
        container.addView(launchBtn)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    private fun createPermissionRow(
        parent: LinearLayout,
        title: String,
        actionText: String,
        onClick: () -> Unit
    ): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = dp2px(6)
                setMargins(0, m, 0, m)
            }
        }

        val label = TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btn = TextView(this).apply {
            text = actionText
            textSize = 11f
            setTextColor(Color.parseColor("#90CAF9"))
            setPadding(dp2px(8), dp2px(4), dp2px(8), dp2px(4))
            setOnClickListener { onClick() }
        }

        row.addView(label)
        row.addView(btn)
        parent.addView(row)
        return btn
    }

    private fun createNavCard(
        parent: LinearLayout,
        title: String,
        desc: String,
        onClick: () -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp2px(8).toFloat()
                setColor(Color.parseColor("#1E1E24"))
                setStroke(1, Color.parseColor("#33FFFFFF"))
            }
            val p = dp2px(12)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(8) }
            setOnClickListener { onClick() }
        }

        val titleTv = TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val descTv = TextView(this).apply {
            text = desc
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(2) }
        }

        card.addView(titleTv)
        card.addView(descTv)
        parent.addView(card)
    }

    private fun refreshPermissionStatus() {
        val isAccessibilityOk = BossAccessibilityService.isConnected()
        accessibilityStatusTv.text = if (isAccessibilityOk) "✅ 已就绪" else "前往开启 ➔"
        accessibilityStatusTv.setTextColor(if (isAccessibilityOk) Color.parseColor("#4CAF50") else Color.parseColor("#FFA726"))

        val isOverlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        overlayStatusTv.text = if (isOverlayOk) "✅ 已就绪" else "前往授权 ➔"
        overlayStatusTv.setTextColor(if (isOverlayOk) Color.parseColor("#4CAF50") else Color.parseColor("#FFA726"))

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || pm.isIgnoringBatteryOptimizations(packageName)
        batteryStatusTv.text = if (isBatteryOk) "✅ 已加白" else "去加入 ➔"
        batteryStatusTv.setTextColor(if (isBatteryOk) Color.parseColor("#4CAF50") else Color.parseColor("#FFA726"))
    }

    private fun handleLaunchAgent() {
        if (!BossAccessibilityService.isConnected()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }

        val app = application as BossApp
        if (!app.configRepository.hasValidApiKey()) {
            Toast.makeText(this, "请先在上方配置 DeepSeek API Key", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ApiKeyConfigActivity::class.java))
            return
        }

        // 1. 尝试拉起 Boss 直聘应用
        var launchSuccess = false
        val bossIntent = packageManager.getLaunchIntentForPackage(BOSS_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        if (bossIntent != null) {
            try {
                startActivity(bossIntent)
                launchSuccess = true
            } catch (e: Exception) {
                Toast.makeText(this, "拉起 Boss 直聘异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "未检测到 Boss 直聘安装，请手动打开", Toast.LENGTH_LONG).show()
        }

        // 2. 开启悬浮窗服务
        FloatingHUDService.startService(this)

        // 3. 启动任务调度器：注入 4000ms 预热倒计时缓冲，等待 Boss 直聘界面渲染
        app.taskDispatcher.start(warmUpDelayMs = 4000L)

        val tipText = if (launchSuccess) "🎉 正在跳转至 Boss 直聘，将在 4 秒预热后自动寻岗！" else "🎉 悬浮窗已就绪，请手动打开 Boss 直聘"
        Toast.makeText(this, tipText, Toast.LENGTH_SHORT).show()
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
