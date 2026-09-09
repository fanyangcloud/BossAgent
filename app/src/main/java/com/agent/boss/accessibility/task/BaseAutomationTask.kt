package com.agent.boss.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.boss.data.pref.AppSettings
import com.agent.boss.dispatcher.contract.DispatcherBroadcasts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * 任务回调接口
 */
interface TaskCallback {
    fun onTaskFinished(taskName: String)
    fun onTaskFailed(taskName: String, reason: String)
}

/**
 * 自动化原子任务抽象基类 (重构优化版 BaseComplexTask)
 */
abstract class BaseAutomationTask {

    protected lateinit var service: AccessibilityService
    protected var callback: TaskCallback? = null
    protected val mainHandler = Handler(Looper.getMainLooper())

    private val appSettings by lazy { AppSettings.getInstance(service) }

    protected var currentStep: Int = 0
    protected var retryCount: Int = 0
    protected open val maxRetryCount: Int = 5

    /** 任务最大运行基准时长 (默认 2 分钟，实际会受倍速因子放大/缩小) */
    protected open var baseTaskDurationMs: Long = 2 * 60 * 1000L

    @Volatile
    protected var isTaskRunning: Boolean = false
    private var isPausedLooping: Boolean = false

    private val timeoutRunnable = Runnable {
        if (isTaskRunning) {
            val realTimeoutSec = getAdjustedDelay(baseTaskDurationMs) / 1000
            Log.e(getTaskName(), "========== 任务执行超时 (${realTimeoutSec}秒) ==========")
            sendTaskMessage("任务超时强制失败_fc")
            failedTask("任务执行超时")
        }
    }

    /**
     * 任务执行入口
     */
    open fun execute(service: AccessibilityService, callback: TaskCallback) {
        this.service = service
        this.callback = callback
        this.currentStep = 1
        this.retryCount = 0
        this.isTaskRunning = true

        startTimeoutMonitor()
        processStep()
    }

    // ==================== 速度倍率控制 ====================

    protected fun getSpeedFactor(): Float = appSettings.getSpeedFactor()

    protected fun getAdjustedDelay(originDelay: Long): Long {
        return (originDelay * getSpeedFactor()).toLong()
    }

    // ==================== 步骤调度有限状态机 ====================

    private fun processStep() {
        if (!isTaskRunning) {
            Log.w(getTaskName(), "任务已停止，跳过第 $currentStep 步")
            return
        }

        // 挂起检查
        if (appSettings.isPaused()) {
            if (!isPausedLooping) {
                Log.i(getTaskName(), "检测到挂起信号，任务暂停中...")
                sendTaskMessage("任务已暂停，等待恢复...")
                stopTimeoutMonitor()
                isPausedLooping = true
            }
            mainHandler.postDelayed({ processStep() }, 1500L)
            return
        }

        if (isPausedLooping) {
            Log.i(getTaskName(), "暂停结束，恢复执行第 $currentStep 步")
            sendTaskMessage("任务恢复执行")
            isPausedLooping = false
            startTimeoutMonitor()
        }

        Log.d(getTaskName(), "正在执行第 $currentStep 步")
        try {
            onStep(currentStep)
        } catch (e: Exception) {
            Log.e(getTaskName(), "第 $currentStep 步执行崩溃: ${e.message}", e)
            failedTask("步骤内部异常: ${e.message}")
        }
    }

    /**
     * 步骤核心分发抽象方法
     */
    protected abstract fun onStep(step: Int)

    /**
     * 顺序推进到下一步 (step++)
     */
    protected fun nextStep(delayMillis: Long) {
        retryCount = 0
        val realDelay = getAdjustedDelay(delayMillis)
        mainHandler.postDelayed({
            currentStep++
            processStep()
        }, realDelay)
    }

    /**
     * 跳转至任意指定步骤
     */
    protected fun sendStep(targetStep: Int, delayMillis: Long) {
        retryCount = 0
        val realDelay = getAdjustedDelay(delayMillis)
        mainHandler.postDelayed({
            currentStep = targetStep
            processStep()
        }, realDelay)
    }

    /**
     * 步骤重试 (超出重试次数判定失败)
     */
    protected fun retryCurrentStep(delayMillis: Long, retryLog: String = "目标未出现") {
        if (!isTaskRunning) return

        if (retryCount >= maxRetryCount) {
            val failReason = "第 $currentStep 步重试超限 ($maxRetryCount 次)"
            Log.e(getTaskName(), failReason)
            sendTaskMessage(failReason)
            failedTask(failReason)
            return
        }

        retryCount++
        val realDelay = getAdjustedDelay(delayMillis)
        sendTaskMessage("$retryLog，${realDelay}ms 后第 $retryCount 次重试_fc")
        mainHandler.postDelayed({ processStep() }, realDelay)
    }

    /**
     * 任务正常完成
     */
    protected open fun finishTask() {
        if (!isTaskRunning) return
        stopTimeoutMonitor()
        isTaskRunning = false
        sendTaskMessage("✅ 任务顺利完成_fc")
        callback?.onTaskFinished(getTaskName())
    }

    /**
     * 任务失败回调
     */
    protected open fun failedTask(reason: String) {
        tryDumpHierarchyOnFailure()
        stopTimeoutMonitor()
        isTaskRunning = false
        sendTaskMessage("❌ 任务失败: $reason _fc")
        callback?.onTaskFailed(getTaskName(), reason)
    }

    // ==================== 超时与强杀 ====================

    private fun startTimeoutMonitor() {
        mainHandler.removeCallbacks(timeoutRunnable)
        if (baseTaskDurationMs > 0) {
            val realTimeout = getAdjustedDelay(baseTaskDurationMs)
            mainHandler.postDelayed(timeoutRunnable, realTimeout)
        }
    }

    private fun stopTimeoutMonitor() {
        mainHandler.removeCallbacks(timeoutRunnable)
    }

    /**
     * 子类可覆写：当任务被强制终止时，唤醒并解脱挂起的协程 Deferred
     */
    open fun onForceStopped() {}

    /**
     * 外部静默强杀接口
     */
    fun forceStopSilently() {
        if (isTaskRunning) {
            Log.e(getTaskName(), "收到强制终止指令，清空动作队列并关闭监控")
            isTaskRunning = false
            stopTimeoutMonitor()
            mainHandler.removeCallbacksAndMessages(null)
            onForceStopped() // 🌟【新增】：强杀时触发子类兜底，避免协程悬挂死锁
        }
    }

    // ==================== 广播通知与现场抓取 ====================

    protected fun sendTaskMessage(message: String) {
        val intent = Intent(DispatcherBroadcasts.ACTION_TASK_STEP_MESSAGE).apply {
            putExtra(DispatcherBroadcasts.EXTRA_TASK_NAME, getTaskName())
            putExtra(DispatcherBroadcasts.EXTRA_STEP_INFO, message)
            setPackage(service.packageName)
        }
        try {
            service.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(getTaskName(), "广播发送异常: ${e.message}")
        }
    }

    /**
     * 任务失败时 1/5 概率导出当前页面的 XML 结构用于排查 Bug
     */
    private fun tryDumpHierarchyOnFailure() {
        try {
            if (Random().nextInt(5) != 0) return
            val root = service.rootInActiveWindow ?: return

            val sb = StringBuilder()
            dumpNodeRecursive(root, sb, 0)
            val xmlDump = sb.toString()
            root.recycle()

            Log.e("TaskFailureDump", "===== 任务失败现场转储 (${getTaskName()}) =====\n$xmlDump")
        } catch (e: Exception) {
            Log.e(getTaskName(), "转储布局树失败: ${e.message}")
        }
    }

    private fun dumpNodeRecursive(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val className = node.className ?: "view"
        val text = node.text?.toString()?.replace("\"", "'") ?: ""
        val desc = node.contentDescription?.toString()?.replace("\"", "'") ?: ""
        val viewId = node.viewIdResourceName ?: ""

        sb.append("$indent<$className bounds=\"${bounds.toShortString()}\" id=\"$viewId\" text=\"$text\" desc=\"$desc\" clickable=\"${node.isClickable}\"")
        val count = node.childCount
        if (count == 0) {
            sb.append(" />\n")
        } else {
            sb.append(">\n")
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    dumpNodeRecursive(child, sb, depth + 1)
                    child.recycle()
                }
            }
            sb.append("$indent</$className>\n")
        }
    }

    open fun getTaskName(): String = this::class.java.simpleName
}
