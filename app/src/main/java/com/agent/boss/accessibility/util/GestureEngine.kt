package com.agent.boss.accessibility.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.resume

/**
 * 拟人化手势执行引擎
 * 针对反爬风控做轨迹非线性平滑、点击随机偏移、手势期间悬浮窗智能避让穿透
 */
object GestureEngine {

    private const val TAG = "GestureEngine"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 悬浮窗可触摸状态监听器（当开始手势时回调 false 开启穿透，手势完成回调 true 恢复交互）
     */
    var windowTouchListener: ((isTouchable: Boolean) -> Unit)? = null

    // ==================== 拟人点击模块 ====================

    /**
     * 点击指定节点，自动计算 25% 内缩安全边距并添加随机散布坐标
     */
    fun performClick(
        service: AccessibilityService,
        node: AccessibilityNodeInfo?,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        if (node == null) {
            onFail?.invoke()
            return false
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val width = bounds.width()
        val height = bounds.height()

        // 异常宽高或零尺寸兜底走原生 action
        if (width <= 0 || height <= 0 || bounds.centerX() <= 0 || bounds.centerY() <= 0) {
            val res = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (res) onSuccess?.invoke() else onFail?.invoke()
            return res
        }

        // 内缩 25% 安全边距
        val safePaddingX = maxOf(2, (width * 0.25).toInt())
        val safePaddingY = maxOf(2, (height * 0.25).toInt())

        val minX = bounds.left + safePaddingX
        val maxX = bounds.right - safePaddingX
        val minY = bounds.top + safePaddingY
        val maxY = bounds.bottom - safePaddingY

        val targetX = if (minX < maxX) ThreadLocalRandom.current().nextInt(minX, maxX + 1) else bounds.centerX()
        val targetY = if (minY < maxY) ThreadLocalRandom.current().nextInt(minY, maxY + 1) else bounds.centerY()

        return clickAt(service, targetX, targetY, onSuccess, onFail)
    }

    /**
     * 点击物理坐标 (x, y)，加入微小触摸时长抖动
     */
    fun clickAt(
        service: AccessibilityService,
        x: Int,
        y: Int,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        // 拟人触屏按压时长：65ms ~ 110ms
        val duration = ThreadLocalRandom.current().nextLong(65, 110)

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGestureInternal(service, gesture, onSuccess, onFail)
        return true
    }

    // ==================== 贝塞尔曲线滑动模块 ====================

    /**
     * 拟人化向上滑动（浏览下一屏岗位）
     * 采用大拇指微弧度轨迹 + 三阶贝塞尔曲线插值
     */
    fun swipeUp(
        service: AccessibilityService,
        durationMs: Long = 0,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        val bounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val screenW = bounds.width()
        val screenH = bounds.height()

        // 起点与终点：从屏幕约 75% 处滑动至 25% 处
        val startX = (screenW * ThreadLocalRandom.current().nextDouble(0.45, 0.55)).toInt()
        val startY = (screenH * ThreadLocalRandom.current().nextDouble(0.72, 0.78)).toInt()

        val endX = (screenW * ThreadLocalRandom.current().nextDouble(0.42, 0.58)).toInt()
        val endY = (screenH * ThreadLocalRandom.current().nextDouble(0.20, 0.28)).toInt()

        // 拟人滑动速度：600ms ~ 900ms
        val actualDuration = if (durationMs > 0) durationMs else ThreadLocalRandom.current().nextLong(650, 920)

        return swipeBezier(service, startX, startY, endX, endY, actualDuration, onSuccess, onFail)
    }

    /**
     * 使用三阶贝塞尔曲线拟合自然人手滑动轨迹
     */
    fun swipeBezier(
        service: AccessibilityService,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())

            val deltaX = (endX - startX).toFloat()
            val deltaY = (endY - startY).toFloat()

            // 模拟人体大拇指划动时的轻微弧形偏角 (Jitter offset)
            val thumbArcJitter = ThreadLocalRandom.current().nextInt(-50, 50).toFloat()

            // 计算两个控制点，构成自然弧线
            val control1X = startX + (deltaX * 0.25f) + thumbArcJitter
            val control1Y = startY + (deltaY * 0.35f)

            val control2X = startX + (deltaX * 0.75f) + (thumbArcJitter * 0.6f)
            val control2Y = startY + (deltaY * 0.85f)

            cubicTo(control1X, control1Y, control2X, control2Y, endX.toFloat(), endY.toFloat())
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGestureInternal(service, gesture, onSuccess, onFail)
        return true
    }

    // ==================== 协程挂起 API (现代语法支持) ====================

    /**
     * 协程挂起式点击，直至手势完成返回结果
     */
    suspend fun clickSuspend(service: AccessibilityService, node: AccessibilityNodeInfo?): Boolean =
        suspendCancellableCoroutine { continuation ->
            performClick(
                service = service,
                node = node,
                onSuccess = { if (continuation.isActive) continuation.resume(true) },
                onFail = { if (continuation.isActive) continuation.resume(false) }
            )
        }

    /**
     * 协程挂起式上滑，直至滑动完成返回结果
     */
    suspend fun swipeUpSuspend(service: AccessibilityService, durationMs: Long = 0): Boolean =
        suspendCancellableCoroutine { continuation ->
            swipeUp(
                service = service,
                durationMs = durationMs,
                onSuccess = { if (continuation.isActive) continuation.resume(true) },
                onFail = { if (continuation.isActive) continuation.resume(false) }
            )
        }

    // ==================== 悬浮窗状态协同分发机制 ====================

    private fun dispatchGestureInternal(
        service: AccessibilityService,
        gesture: GestureDescription,
        onSuccess: (() -> Unit)?,
        onFail: (() -> Unit)?
    ) {
        // 1. 下发前：悬浮窗切为不可触摸，确保手势完全穿透到底层 App
        windowTouchListener?.invoke(false)

        try {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    restoreWindowTouchable()
                    onSuccess?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    restoreWindowTouchable()
                    Log.w(TAG, "手势下发被系统取消")
                    onFail?.invoke()
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "手势派发异常: ${e.message}")
            restoreWindowTouchable()
            onFail?.invoke()
        }
    }

    private fun restoreWindowTouchable() {
        // 延迟 150ms 恢复悬浮窗交互，防止手势刚松手时的瞬间抬起事件被悬浮窗吞掉
        mainHandler.postDelayed({
            windowTouchListener?.invoke(true)
        }, 150L)
    }
}
