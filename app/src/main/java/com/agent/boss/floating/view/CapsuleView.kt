package com.agent.boss.floating.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.agent.boss.floating.state.HUDUiState
import kotlin.math.abs

/**
 * 灵动胶囊态小悬浮窗 (极简防遮挡、自动贴边吸附)
 */
class CapsuleView(context: Context) : FrameLayout(context) {

    private val dotView: FrameLayout
    private val titleTv: TextView

    var onExpandClicked: (() -> Unit)? = null
    var onPositionMoved: ((deltaX: Int, deltaY: Int) -> Unit)? = null
    var onDragReleased: (() -> Unit)? = null

    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var isDragging = false

    init {
        // 胶囊背景：半透明圆角黑色磨砂
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.parseColor("#D918181C"))
            setStroke(2, Color.parseColor("#4DFFFFFF"))
        }

        val paddingHorizontal = dp2px(12)
        val paddingVertical = dp2px(6)
        setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // 状态呼吸灯圆点
        dotView = FrameLayout(context).apply {
            val dotSize = dp2px(8)
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginEnd = dp2px(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50"))
            }
        }

        titleTv = TextView(context).apply {
            text = "BossAgent"
            textSize = 12f
            setTextColor(Color.WHITE)
            isSingleLine = true
        }

        container.addView(dotView)
        container.addView(titleTv)
        addView(container)
    }

    fun render(state: HUDUiState) {
        titleTv.text = state.stateTitle
        try {
            val color = Color.parseColor(state.indicatorColorHex)
            (dotView.background as? GradientDrawable)?.setColor(color)
        } catch (e: Exception) {
            // 颜色容错
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownRawX = event.rawX
                touchDownRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - lastRawX).toInt()
                val dy = (event.rawY - lastRawY).toInt()

                if (abs(event.rawX - touchDownRawX) > 10 || abs(event.rawY - touchDownRawY) > 10) {
                    isDragging = true
                }

                if (isDragging) {
                    onPositionMoved?.invoke(dx, dy)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    // 单击展开控制台
                    onExpandClicked?.invoke()
                } else {
                    onDragReleased?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dp2px(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
