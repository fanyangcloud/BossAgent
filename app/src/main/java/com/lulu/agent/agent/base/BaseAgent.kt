package com.lulu.agent.agent.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.lulu.agent.dispatcher.contract.DispatcherBroadcasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 智能体抽象基类
 */
abstract class BaseAgent(
    protected val context: Context,
    val agentName: String
) {

    protected val tag: String = agentName

    @Volatile
    protected var isRunning: Boolean = false

    // 每个 Agent 独享的协程作用域，互相隔离崩溃
    protected var agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val registeredReceivers = mutableListOf<BroadcastReceiver>()

    open fun start() {
        if (isRunning) return
        isRunning = true
        agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        Log.i(tag, "[$agentName] 已激活运行")
    }

    open fun stop() {
        if (!isRunning) return
        isRunning = false
        unregisterAllReceivers()
        agentScope.cancel()
        Log.w(tag, "[$agentName] 已挂起停止")
    }

    fun isAlive(): Boolean = isRunning

    /**
     * 安全注册内部广播监听
     */
    protected fun registerReceiver(filter: IntentFilter, onReceiveAction: (Intent) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent != null && isRunning) {
                    onReceiveAction(intent)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registeredReceivers.add(receiver)
    }

    private fun unregisterAllReceivers() {
        registeredReceivers.forEach {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(tag, "解绑广播异常: ${e.message}")
            }
        }
        registeredReceivers.clear()
    }

    /**
     * 向系统广播投递 Agent 日志
     */
    protected fun sendAgentLog(message: String, isHighlight: Boolean = false) {
        val intent = Intent(DispatcherBroadcasts.ACTION_TASK_STEP_MESSAGE).apply {
            putExtra(DispatcherBroadcasts.EXTRA_TASK_NAME, agentName)
            putExtra(DispatcherBroadcasts.EXTRA_STEP_INFO, message)
            setPackage(context.packageName)
        }
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(tag, "发送日志广播失败: ${e.message}")
        }
    }

    /**
     * 发送应用内广播事件
     */
    protected fun sendBroadcastEvent(action: String, extraBuilder: (Intent.() -> Unit)? = null) {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
            extraBuilder?.invoke(this)
        }
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(tag, "发送事件广播失败: ${e.message}")
        }
    }
}
