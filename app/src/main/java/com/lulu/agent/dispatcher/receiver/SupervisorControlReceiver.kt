package com.lulu.agent.dispatcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.lulu.agent.dispatcher.contract.DispatcherBroadcasts

/**
 * 调度中枢控制指令接收器
 */
class SupervisorControlReceiver : BroadcastReceiver() {

    var onStartCommand: (() -> Unit)? = null
    var onStopCommand: (() -> Unit)? = null
    var onPauseCommand: (() -> Unit)? = null
    var onResumeCommand: (() -> Unit)? = null
    var onEmergencyStopCommand: ((riskType: String, reason: String) -> Unit)? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            DispatcherBroadcasts.ACTION_TASK_START -> onStartCommand?.invoke()
            DispatcherBroadcasts.ACTION_TASK_STOP -> onStopCommand?.invoke()
            DispatcherBroadcasts.ACTION_TASK_PAUSE -> onPauseCommand?.invoke()
            DispatcherBroadcasts.ACTION_TASK_RESUME -> onResumeCommand?.invoke()
            DispatcherBroadcasts.ACTION_EMERGENCY_STOP -> {
                val riskType = intent.getStringExtra(DispatcherBroadcasts.EXTRA_RISK_TYPE) ?: "MANUAL"
                val reason = intent.getStringExtra(DispatcherBroadcasts.EXTRA_ERROR_MESSAGE) ?: "紧急中止"
                onEmergencyStopCommand?.invoke(riskType, reason)
            }
        }
    }

    companion object {
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(DispatcherBroadcasts.ACTION_TASK_START)
                addAction(DispatcherBroadcasts.ACTION_TASK_STOP)
                addAction(DispatcherBroadcasts.ACTION_TASK_PAUSE)
                addAction(DispatcherBroadcasts.ACTION_TASK_RESUME)
                addAction(DispatcherBroadcasts.ACTION_EMERGENCY_STOP)
            }
        }
    }
}
