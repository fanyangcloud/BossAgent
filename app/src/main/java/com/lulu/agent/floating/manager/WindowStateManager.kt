package com.lulu.agent.floating.manager

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.lulu.agent.floating.view.CapsuleView
import com.lulu.agent.floating.view.ConsoleDashboardView

/**
 * 悬浮窗状态管理器：支持胶囊/面板形态切换与手势自动避让穿透
 */
class WindowStateManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    enum class Mode {
        CAPSULE,
        CONSOLE,
        HIDDEN
    }

    private var currentMode = Mode.HIDDEN
    private var capsuleView: CapsuleView? = null
    private var consoleView: ConsoleDashboardView? = null

    private lateinit var capsuleParams: WindowManager.LayoutParams
    private lateinit var consoleParams: WindowManager.LayoutParams

    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels

        initLayoutParams()
    }

    private fun initLayoutParams() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 胶囊 LayoutParams
        capsuleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - dp2px(120)
            y = screenHeight / 3
        }

        // 控制台 LayoutParams (占屏幕宽度的 86%)
        val consoleWidth = (screenWidth * 0.86).toInt()
        consoleParams = WindowManager.LayoutParams(
            consoleWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp2px(80)
        }
    }

    fun attachViews(capsule: CapsuleView, console: ConsoleDashboardView) {
        this.capsuleView = capsule
        this.consoleView = console

        setupInteractions()
    }

    private fun setupInteractions() {
        capsuleView?.onPositionMoved = { dx, dy ->
            capsuleParams.x += dx
            capsuleParams.y += dy
            updateLayout(capsuleView, capsuleParams)
        }

        capsuleView?.onDragReleased = {
            // 贴边自动磁吸 (左或右)
            val currentX = capsuleParams.x
            capsuleParams.x = if (currentX < screenWidth / 2) dp2px(8) else screenWidth - (capsuleView?.width ?: dp2px(100)) - dp2px(8)
            updateLayout(capsuleView, capsuleParams)
        }

        consoleView?.onPositionMoved = { dx, dy ->
            consoleParams.x += dx
            consoleParams.y += dy
            updateLayout(consoleView, consoleParams)
        }
    }

    fun switchToCapsule() {
        if (currentMode == Mode.CAPSULE) return
        removeCurrent()
        capsuleView?.let {
            windowManager.addView(it, capsuleParams)
            currentMode = Mode.CAPSULE
        }
    }

    fun switchToConsole() {
        if (currentMode == Mode.CONSOLE) return
        removeCurrent()
        consoleView?.let {
            windowManager.addView(it, consoleParams)
            currentMode = Mode.CONSOLE
        }
    }

    fun removeCurrent() {
        when (currentMode) {
            Mode.CAPSULE -> capsuleView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            Mode.CONSOLE -> consoleView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
            Mode.HIDDEN -> Unit
        }
        currentMode = Mode.HIDDEN
    }

    /**
     * 手势期间开启穿透（FLAG_NOT_TOUCHABLE）
     */
    fun setTouchable(isTouchable: Boolean) {
        val targetParams = when (currentMode) {
            Mode.CAPSULE -> capsuleParams
            Mode.CONSOLE -> consoleParams
            Mode.HIDDEN -> return
        }

        if (isTouchable) {
            targetParams.flags = targetParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            targetParams.flags = targetParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val targetView = if (currentMode == Mode.CAPSULE) capsuleView else consoleView
        updateLayout(targetView, targetParams)
    }

    private fun updateLayout(view: View?, params: WindowManager.LayoutParams) {
        if (view != null && view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                // 忽略窗口销毁中的竞态错误
            }
        }
    }

    private fun dp2px(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
