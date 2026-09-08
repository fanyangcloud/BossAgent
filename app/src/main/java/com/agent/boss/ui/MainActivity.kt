package com.agent.boss.ui

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.agent.boss.BossApp
import com.agent.boss.accessibility.BossAccessibilityService
import com.agent.boss.dispatcher.contract.DispatcherBroadcasts
import com.agent.boss.dispatcher.fsm.EngineState
import com.agent.boss.floating.FloatingHUDService
import com.agent.boss.ui.dashboard.HistoryRecordActivity
import com.agent.boss.ui.settings.ApiKeyConfigActivity
import com.agent.boss.ui.settings.ResumeEditorActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 自动化求职助手 - 主控制台 (全新清新卡片风)
 */
class MainActivity : AppCompatActivity() {

    private val bossPackage = "com.hpbr.bosszhipin"

    // UI 引用
    private lateinit var accessibilityBtn: TextView
    private lateinit var overlayBtn: TextView
    private lateinit var statusTextTv: TextView
    private lateinit var percentageTv: TextView
    private lateinit var mainActionButton: TextView

    // 数据看板四个计数器
    private lateinit var interviewCountTv: TextView
    private lateinit var chatCountTv: TextView
    private lateinit var deliveryCountTv: TextView
    private lateinit var contactCountTv: TextView

    // 颜色规范
    private val colorBg = Color.parseColor("#F5F7FA")
    private val colorCard = Color.WHITE
    private val colorCardStroke = Color.parseColor("#E4E9F0")
    private val colorPrimary = Color.parseColor("#1B72E8")
    private val colorPrimarySoft = Color.parseColor("#EBF3FD")
    private val colorTextMain = Color.parseColor("#1C2430")
    private val colorTextSub = Color.parseColor("#748398")
    private val colorSuccess = Color.parseColor("#2E7D32")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(buildContentView())
        registerStatusReceiver()
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusReceiver)
    }

    // ==================== 1:1 动态纯代码构建视图 ====================

    private fun buildContentView(): View {
        val root = RelativeLayout(this).apply {
            setBackgroundColor(colorBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. 顶部固定标题栏
        val topBar = createTopBar().apply { id = View.generateViewId() }
        val topBarParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
        root.addView(topBar, topBarParams)

        // 2. 底部固定导航栏
        val bottomNav = createBottomNav().apply { id = View.generateViewId() }
        val bottomNavParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            dp2px(64)
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
        root.addView(bottomNav, bottomNavParams)

        // 3. 中间可滚动卡片流
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val scrollParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.BELOW, topBar.id)
            addRule(RelativeLayout.ABOVE, bottomNav.id)
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp2px(16)
            setPadding(pad, dp2px(10), pad, dp2px(20))
        }

        // 核心板块装配
        contentLayout.addView(createPermissionCardsGrid())
        contentLayout.addView(createRunningStatusCard())
        contentLayout.addView(createDataDashboardCard())

        scrollView.addView(contentLayout)
        root.addView(scrollView, scrollParams)

        return root
    }

    /**
     * 顶部标题栏 + 右侧设置齿轮
     */
    private fun createTopBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(20), dp2px(42), dp2px(20), dp2px(14))
            setBackgroundColor(colorBg)

            val title = TextView(this@MainActivity).apply {
                text = "自动化求职助手"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextMain)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val gearBtn = TextView(this@MainActivity).apply {
                text = "⚙️"
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(dp2px(32), dp2px(32))
                setOnClickListener { showSettingsDialog() }
            }

            addView(title)
            addView(gearBtn)
        }
    }

    /**
     * 并排双列卡片：无障碍权限 + 悬浮窗权限 (矢量图标 + 居中对齐)
     */
    private fun createPermissionCardsGrid(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 左卡片：无障碍权限 (使用自绘纯白矢量人形图标，不再依赖 Emoji，彻底解决隐形与错位)
        val leftCard = createSinglePermCard(
            iconDrawable = createAccessibilityIcon(),
            title = "无障碍权限",
            subtitle = "用于模拟点击、滑动等操作",
            onAction = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        ).also { accessibilityBtn = it.second }

        // 右卡片：悬浮窗权限 (使用自绘纯白矢量多任务窗口图标)
        val rightCard = createSinglePermCard(
            iconDrawable = createOverlayIcon(),
            title = "悬浮窗权限",
            subtitle = "用于显示悬浮控制面板",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        ).also { overlayBtn = it.second }

        val leftParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp2px(8)
        }
        val rightParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp2px(8)
        }

        row.addView(leftCard.first, leftParams)
        row.addView(rightCard.first, rightParams)
        return row
    }

    private fun createSinglePermCard(
        iconDrawable: Drawable,
        title: String,
        subtitle: String,
        onAction: () -> Unit
    ): Pair<View, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createCardDrawable()
            setPadding(dp2px(12), dp2px(20), dp2px(12), dp2px(18))
        }

        // 圆形蓝色底图标容器
        val iconContainer = FrameLayout(this).apply {
            val sz = dp2px(54)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorPrimary)
            }
        }

        val iconIv = ImageView(this).apply {
            setImageDrawable(iconDrawable)
            val iconSz = dp2px(28)
            layoutParams = FrameLayout.LayoutParams(iconSz, iconSz, Gravity.CENTER)
        }
        iconContainer.addView(iconIv)

        val titleTv = TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(12)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val subTv = TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(colorTextSub)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setLineSpacing(dp2px(2).toFloat(), 1.0f)
            minLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(6)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val actionBtn = TextView(this).apply {
            text = "去开启"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(100).toFloat()
                setStroke(dp2px(1), colorPrimary)
                setColor(Color.WHITE)
            }
            setPadding(dp2px(24), dp2px(7), dp2px(24), dp2px(7))
            setOnClickListener { onAction() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(16)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        card.addView(iconContainer)
        card.addView(titleTv)
        card.addView(subTv)
        card.addView(actionBtn)

        return Pair(card, actionBtn)
    }

    /**
     * 运行状态卡片 + 启动主按钮
     */
    private fun createRunningStatusCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardDrawable()
            setPadding(dp2px(18), dp2px(18), dp2px(18), dp2px(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(16) }
        }

        // 上半段：运行状态 + 环形进度圈
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val statusLabel = TextView(this).apply {
            text = "运行状态: "
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
        }

        statusTextTv = TextView(this).apply {
            text = "待启动"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            includeFontPadding = false
        }

        val statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(statusLabel)
            addView(statusTextTv)
        }

        // 环形进度徽标
        val progressCircle = FrameLayout(this).apply {
            val sz = dp2px(42)
            layoutParams = LinearLayout.LayoutParams(sz, sz)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp2px(3), colorPrimarySoft)
            }
        }
        percentageTv = TextView(this).apply {
            text = "0%"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        progressCircle.addView(percentageTv)

        topRow.addView(statusContainer)
        topRow.addView(progressCircle)
        card.addView(topRow)

        // 下半段：启动任务主按钮
        mainActionButton = TextView(this).apply {
            text = "▶ 启动任务"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(10).toFloat()
                setColor(colorPrimary)
            }
            val bp = dp2px(14)
            setPadding(bp, bp, bp, bp)
            setOnClickListener { handleMainActionClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(18) }
        }

        card.addView(mainActionButton)
        return card
    }

    /**
     * 四列数据看板 (已约面试、今日沟通、今日投递、联系交换)
     */
    private fun createDataDashboardCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createCardDrawable()
            setPadding(dp2px(12), dp2px(20), dp2px(12), dp2px(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp2px(16) }
        }

        val (c1, tv1) = createDataColumn("📅", "已约面试", "0")
        val (c2, tv2) = createDataColumn("💬", "今日沟通", "0")
        val (c3, tv3) = createDataColumn("✈️", "今日投递", "0")
        val (c4, tv4) = createDataColumn("📇", "联系交换", "0")

        interviewCountTv = tv1
        chatCountTv = tv2
        deliveryCountTv = tv3
        contactCountTv = tv4

        card.addView(c1)
        card.addView(c2)
        card.addView(c3)
        card.addView(c4)

        return card
    }

    private fun createDataColumn(
        icon: String,
        label: String,
        initialValue: String
    ): Pair<View, TextView> {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // 固定高度和宽度，避免不同 Emoji 固有 Bounds 导致的水平参差不齐
        val iconTv = TextView(this).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                dp2px(28),
                dp2px(28)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val labelTv = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(colorTextSub)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(6)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val countTv = TextView(this).apply {
            text = initialValue
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(6)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        col.addView(iconTv)
        col.addView(labelTv)
        col.addView(countTv)

        return Pair(col, countTv)
    }

    /**
     * 底部常驻导航栏
     */
    private fun createBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp2px(8).toFloat()
        }

        val tabHome = createNavItem("🏠", "首页", isSelected = true) {
            // 当前即为首页
        }
        val tabRecords = createNavItem("📋", "任务记录", isSelected = false) {
            startActivity(Intent(this, HistoryRecordActivity::class.java))
        }
        val tabMine = createNavItem("👤", "我的", isSelected = false) {
            showSettingsDialog()
        }

        nav.addView(tabHome)
        nav.addView(tabRecords)
        nav.addView(tabMine)

        return nav
    }

    private fun createNavItem(
        icon: String,
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { onClick() }

            val iconTv = TextView(this@MainActivity).apply {
                text = icon
                textSize = 18f
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    dp2px(24),
                    dp2px(24)
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            val labelTv = TextView(this@MainActivity).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                includeFontPadding = false
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) colorPrimary else colorTextSub)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp2px(4)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            addView(iconTv)
            addView(labelTv)
        }
    }

    // ==================== 业务逻辑与状态驱动 ====================

    private fun refreshAllStatus() {
        // 1. 刷新无障碍权限
        val isAccessibilityOk = BossAccessibilityService.isConnected()
        updateButtonStatus(accessibilityBtn, isAccessibilityOk)

        // 2. 刷新悬浮窗权限
        val isOverlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        updateButtonStatus(overlayBtn, isOverlayOk)

        // 3. 刷新引擎状态与主按键文字
        val app = application as BossApp
        val state = app.stateMachine.getCurrentState()
        updateEngineStateUi(state)

        // 4. 从数据库异步查询统计数据
        loadDashboardData(app)
    }

    private fun updateButtonStatus(btn: TextView, isGranted: Boolean) {
        if (isGranted) {
            btn.text = "已开启"
            btn.setTextColor(colorSuccess)
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(100).toFloat()
                setColor(Color.parseColor("#E8F5E9"))
            }
            btn.isEnabled = false
        } else {
            btn.text = "去开启"
            btn.setTextColor(colorPrimary)
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(100).toFloat()
                setStroke(dp2px(1), colorPrimary)
                setColor(Color.WHITE)
            }
            btn.isEnabled = true
        }
    }

    private fun updateEngineStateUi(state: EngineState) {
        statusTextTv.text = state.title
        try {
            statusTextTv.setTextColor(Color.parseColor(state.indicatorColorHex))
        } catch (e: Exception) {
            statusTextTv.setTextColor(colorPrimary)
        }

        if (state.isOperating()) {
            mainActionButton.text = "⏸ 暂停任务"
            mainActionButton.background = GradientDrawable().apply {
                cornerRadius = dp2px(10).toFloat()
                setColor(Color.parseColor("#EF6C00")) // 橙色暂停
            }
        } else {
            mainActionButton.text = "▶ 启动任务"
            mainActionButton.background = GradientDrawable().apply {
                cornerRadius = dp2px(10).toFloat()
                setColor(colorPrimary)
            }
        }
    }

    private fun loadDashboardData(app: BossApp) {
        lifecycleScope.launch {
            val (todayChat, todayApplied) = withContext(Dispatchers.IO) {
                val db = app.database
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val start = cal.timeInMillis
                val end = System.currentTimeMillis()

                val chats = db.jobDao().getCommunicatedCountBetween(start, end)
                Pair(chats, chats)
            }

            chatCountTv.text = todayChat.toString()
            deliveryCountTv.text = todayApplied.toString()

            // 计算今日打招呼上限比例
            val maxCount = app.configRepository.getMaxDailyGreetings()
            val percent = if (maxCount > 0) minOf(100, (todayChat * 100) / maxCount) else 0
            percentageTv.text = "$percent%"
        }
    }

    private fun handleMainActionClick() {
        val app = application as BossApp
        val currentState = app.stateMachine.getCurrentState()

        if (currentState.isOperating()) {
            // 运行中 -> 暂停
            app.taskDispatcher.pause()
            updateEngineStateUi(EngineState.PAUSED)
            return
        }

        if (currentState == EngineState.PAUSED) {
            // 已暂停 -> 恢复
            app.taskDispatcher.resume()
            updateEngineStateUi(EngineState.SCANNING)
            return
        }

        // 待启动 -> 校验并启动
        if (!BossAccessibilityService.isConnected()) {
            Toast.makeText(this, "请先开启【无障碍权限】", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启【悬浮窗权限】", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (!app.configRepository.hasValidApiKey()) {
            Toast.makeText(this, "请先在右上角【设置】中配置 DeepSeek Key", Toast.LENGTH_LONG).show()
            showSettingsDialog()
            return
        }

        // 1. 尝试拉起 Boss 直聘
        val bossIntent = packageManager.getLaunchIntentForPackage(bossPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (bossIntent != null) {
            startActivity(bossIntent)
        } else {
            Toast.makeText(this, "未检测到 Boss 直聘安装", Toast.LENGTH_SHORT).show()
        }

        // 2. 启动悬浮窗服务
        FloatingHUDService.startService(this)

        // 3. 启动任务调度器 (带 4000ms 预热缓冲)
        app.taskDispatcher.start(warmUpDelayMs = 4000L)
        updateEngineStateUi(EngineState.SCANNING)

        Toast.makeText(this, "🚀 鹿鹿已启航，正在跳转 Boss 直聘...", Toast.LENGTH_SHORT).show()
    }

    /**
     * 弹出设置选项弹窗 (修复 Emoji 与中文的垂直对齐错位)
     */
    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp2px(16).toFloat()
                setColor(Color.WHITE)
            }
            val p = dp2px(20)
            setPadding(p, p, p, p)
        }

        val title = TextView(this).apply {
            text = "智能体核心配置"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextMain)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp2px(8) }
        }
        container.addView(title)

        fun createSettingItem(icon: String, text: String, onClick: () -> Unit): View {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp2px(4), dp2px(14), dp2px(4), dp2px(14))
                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }

                val iconTv = TextView(this@MainActivity).apply {
                    this.text = icon
                    textSize = 18f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(dp2px(26), dp2px(26))
                }

                val labelTv = TextView(this@MainActivity).apply {
                    this.text = text
                    textSize = 14f
                    setTextColor(colorTextMain)
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp2px(10) }
                }

                addView(iconTv)
                addView(labelTv)
            }
        }

        container.addView(createSettingItem("🔑", "DeepSeek API Key 配置") {
            startActivity(Intent(this, ApiKeyConfigActivity::class.java))
        })
        container.addView(createSettingItem("📝", "个人简历与背景设定") {
            startActivity(Intent(this, ResumeEditorActivity::class.java))
        })
        container.addView(createSettingItem("🔋", "忽略电池优化 (防断流)") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        })

        dialog.setContentView(container)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    // ==================== 状态同步广播监听 ====================

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DispatcherBroadcasts.ACTION_ENGINE_STATE_CHANGED) {
                val newState = intent.getStringExtra(DispatcherBroadcasts.EXTRA_NEW_STATE) ?: return
                try {
                    val state = EngineState.valueOf(newState)
                    updateEngineStateUi(state)
                    val app = application as BossApp
                    loadDashboardData(app)
                } catch (e: Exception) {
                    // 容错
                }
            }
        }
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter(DispatcherBroadcasts.ACTION_ENGINE_STATE_CHANGED)
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // ==================== 自绘高质感矢量图标 ====================

    /**
     * 自绘国际通用纯白无障碍 (Accessibility) 人形矢量图标
     */
    private fun createAccessibilityIcon(): Drawable {
        return object : Drawable() {
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            override fun draw(canvas: Canvas) {
                val b = bounds
                val w = b.width().toFloat()
                val h = b.height().toFloat()
                if (w <= 0f || h <= 0f) return

                val cx = b.left + w / 2f
                val size = minOf(w, h) * 0.8f
                val startY = b.top + (h - size) / 2f

                strokePaint.strokeWidth = size * 0.12f

                // 头部
                val headRadius = size * 0.13f
                val headCenterY = startY + headRadius
                canvas.drawCircle(cx, headCenterY, headRadius, fillPaint)

                // 展开的双臂
                val armY = startY + size * 0.40f
                val armSpan = size * 0.42f
                canvas.drawLine(cx - armSpan, armY, cx + armSpan, armY, strokePaint)

                // 躯干
                val torsoBottomY = startY + size * 0.66f
                canvas.drawLine(cx, armY, cx, torsoBottomY, strokePaint)

                // 左腿与右腿
                val legSpan = size * 0.30f
                val legBottomY = startY + size
                canvas.drawLine(cx, torsoBottomY, cx - legSpan, legBottomY, strokePaint)
                canvas.drawLine(cx, torsoBottomY, cx + legSpan, legBottomY, strokePaint)
            }

            override fun setAlpha(alpha: Int) {
                strokePaint.alpha = alpha
                fillPaint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                strokePaint.colorFilter = colorFilter
                fillPaint.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    /**
     * 自绘纯白悬浮多任务窗口 (Floating Window) 矢量图标
     */
    private fun createOverlayIcon(): Drawable {
        return object : Drawable() {
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            override fun draw(canvas: Canvas) {
                val b = bounds
                val w = b.width().toFloat()
                val h = b.height().toFloat()
                if (w <= 0f || h <= 0f) return

                val size = minOf(w, h) * 0.8f
                val left = b.left + (w - size) / 2f
                val top = b.top + (h - size) / 2f

                strokePaint.strokeWidth = size * 0.10f
                val corner = size * 0.12f

                // 主背景窗口 (大卡片轮廓)
                val mainRect = RectF(
                    left,
                    top,
                    left + size * 0.82f,
                    top + size * 0.72f
                )
                canvas.drawRoundRect(mainRect, corner, corner, strokePaint)

                // 主窗口顶部标题栏分割线
                val barY = top + size * 0.22f
                canvas.drawLine(left + size * 0.08f, barY, left + size * 0.74f, barY, strokePaint)

                // 前景小悬浮窗 (右下角画中画)
                val floatRect = RectF(
                    left + size * 0.44f,
                    top + size * 0.40f,
                    left + size,
                    top + size * 0.96f
                )
                // 先用主题色填充内部，遮挡背景窗口线条
                fillPaint.color = colorPrimary
                canvas.drawRoundRect(floatRect, corner, corner, fillPaint)
                // 绘制小窗口轮廓
                strokePaint.strokeWidth = size * 0.09f
                canvas.drawRoundRect(floatRect, corner, corner, strokePaint)
                // 内部小亮点
                fillPaint.color = Color.WHITE
                canvas.drawCircle(floatRect.centerX(), floatRect.centerY(), size * 0.06f, fillPaint)
            }

            override fun setAlpha(alpha: Int) {
                strokePaint.alpha = alpha
                fillPaint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                strokePaint.colorFilter = colorFilter
                fillPaint.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    // ==================== 工具函数 ====================

    private fun createCardDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(16).toFloat()
            setColor(colorCard)
            setStroke(dp2px(1), colorCardStroke)
        }
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}