package com.agent.boss.dispatcher.contract

import com.agent.boss.dispatcher.fsm.EngineState

/**
 * 智能体集群内部事件流转载体
 */
sealed class AgentEvent {

    /**
     * Scout Agent 发现并抓取到原始岗位数据
     */
    data class JobDiscovered(
        val jobId: String,
        val title: String,
        val companyName: String,
        val salaryText: String,
        val hrActiveStatus: String,
        val jobDescription: String
    ) : AgentEvent()

    /**
     * Evaluator Agent 完成评估并产出决策报告
     */
    data class EvaluateDecision(
        val jobId: String,
        val companyName: String,
        val isApproved: Boolean,
        val matchScore: Int,
        val reason: String,
        val suggestedGreeting: String
    ) : AgentEvent()

    /**
     * Communicator Agent 发起沟通的结果回执
     */
    data class GreetingResult(
        val jobId: String,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    ) : AgentEvent()

    /**
     * Supervisor Agent 检测到异常或风控
     */
    data class RiskAlert(
        val riskType: String,
        val message: String,
        val requiresManualIntervention: Boolean = true
    ) : AgentEvent()

    /**
     * 引擎状态机发生状态切换
     */
    data class StateTransition(
        val oldState: EngineState,
        val newState: EngineState,
        val reason: String
    ) : AgentEvent()

    /**
     * 业务日志投递事件（供悬浮窗面板展示）
     */
    data class LogMessage(
        val source: String,
        val message: String,
        val isHighlight: Boolean = false
    ) : AgentEvent()
}
