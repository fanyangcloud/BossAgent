package com.agent.boss.agent.supervisor

import android.content.Context
import android.content.IntentFilter
import android.util.Log
import com.agent.boss.accessibility.model.PageScene
import com.agent.boss.agent.base.BaseAgent
import com.agent.boss.dispatcher.contract.DispatcherBroadcasts

/**
 * 安全风控官：守护全系统运行安全，负责快速熔断
 */
class SupervisorAgent(context: Context) : BaseAgent(context, "SupervisorAgent") {

    private var consecutiveFailureCount = 0
    private val maxAllowedFailures = 4 // 连续失败 4 次则自动熔断（防止卡在死循环页面）

    override fun start() {
        super.start()
        consecutiveFailureCount = 0
        registerRiskListeners()
        sendAgentLog("🛡️ 安全风控官已就位，全局守护中")
    }

    private fun registerRiskListeners() {
        val filter = IntentFilter().apply {
            addAction(DispatcherBroadcasts.ACTION_RISK_TRIGGERED)
            addAction(DispatcherBroadcasts.ACTION_PAGE_SCENE_CHANGED)
            addAction(DispatcherBroadcasts.ACTION_TASK_FEEDBACK)
        }

        registerReceiver(filter) { intent ->
            when (intent.action) {
                DispatcherBroadcasts.ACTION_RISK_TRIGGERED -> {
                    val riskType = intent.getStringExtra(DispatcherBroadcasts.EXTRA_RISK_TYPE) ?: "UNKNOWN"
                    val detail = intent.getStringExtra(DispatcherBroadcasts.EXTRA_RISK_DETAIL) ?: "侦测到风控异常"
                    triggerEmergencyStop(riskType, detail)
                }

                DispatcherBroadcasts.ACTION_PAGE_SCENE_CHANGED -> {
                    val sceneName = intent.getStringExtra(DispatcherBroadcasts.EXTRA_SCENE_NAME)
                    if (sceneName == PageScene.CAPTCHA_RISK.name) {
                        triggerEmergencyStop("CAPTCHA_SCENE", "视觉场景检测为验证码拦截页")
                    }
                }

                DispatcherBroadcasts.ACTION_TASK_FEEDBACK -> {
                    val status = intent.getStringExtra(DispatcherBroadcasts.EXTRA_TASK_STATUS)
                    if (status == DispatcherBroadcasts.STATUS_FAILED) {
                        consecutiveFailureCount++
                        Log.w(tag, "任务连续失败计数: $consecutiveFailureCount / $maxAllowedFailures")
                        if (consecutiveFailureCount >= maxAllowedFailures) {
                            triggerEmergencyStop("CONSECUTIVE_FAILURES", "任务连续失败超过 $maxAllowedFailures 次，疑似界面卡死或未知阻断")
                        }
                    } else if (status == DispatcherBroadcasts.STATUS_SUCCESS) {
                        consecutiveFailureCount = 0
                    }
                }
            }
        }
    }

    /**
     * 触发全系统急停熔断
     */
    private fun triggerEmergencyStop(riskType: String, reason: String) {
        val alertMsg = "🚨【安全熔断】原因: $reason (特征: $riskType)，系统已紧急刹车！"
        Log.e(tag, alertMsg)
        sendAgentLog(alertMsg, isHighlight = true)

        // 广播最高优先级的急停指令
        sendBroadcastEvent(DispatcherBroadcasts.ACTION_EMERGENCY_STOP) {
            putExtra(DispatcherBroadcasts.EXTRA_RISK_TYPE, riskType)
            putExtra(DispatcherBroadcasts.EXTRA_ERROR_MESSAGE, reason)
        }
    }
}
