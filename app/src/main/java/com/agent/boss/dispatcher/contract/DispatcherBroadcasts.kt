package com.agent.boss.dispatcher.contract

object DispatcherBroadcasts {

    // ==================== 控制指令 Action (外层 -> 调度器 / 无障碍) ====================
    /** 启动自动化求职调度流程 */
    const val ACTION_TASK_START = "com.agent.boss.action.TASK_START"
    /** 正常终止并重置任务 */
    const val ACTION_TASK_STOP = "com.agent.boss.action.TASK_STOP"
    /** 暂停自动化流水线（保留当前上下文） */
    const val ACTION_TASK_PAUSE = "com.agent.boss.action.TASK_PAUSE"
    /** 从暂停状态恢复执行 */
    const val ACTION_TASK_RESUME = "com.agent.boss.action.TASK_RESUME"
    /** 最高优先级的紧急制动（风控熔断/强制急停） */
    const val ACTION_EMERGENCY_STOP = "com.agent.boss.action.EMERGENCY_STOP"

    // ==================== 感知与反馈 Action (无障碍/Agent -> 调度器/悬浮窗) ====================
    /** 页面场景变化（如从列表进入详情页） */
    const val ACTION_PAGE_SCENE_CHANGED = "com.agent.boss.action.PAGE_SCENE_CHANGED"
    /** 步骤执行日志回传（投递到悬浮窗控制台） */
    const val ACTION_TASK_STEP_MESSAGE = "com.agent.boss.action.TASK_STEP_MESSAGE"
    /** 无障碍底层原子任务完成/失败回执 */
    const val ACTION_TASK_FEEDBACK = "com.agent.boss.action.TASK_FEEDBACK"
    /** 触发平台风控预警（验证码、人脸、频繁操作） */
    const val ACTION_RISK_TRIGGERED = "com.agent.boss.action.RISK_TRIGGERED"
    /** 全局引擎有限状态机变更通知 */
    const val ACTION_ENGINE_STATE_CHANGED = "com.agent.boss.action.ENGINE_STATE_CHANGED"

    // ==================== Extra 字段键名 ====================
    const val EXTRA_SCENE_NAME = "extra_scene_name"
    const val EXTRA_STEP_INFO = "extra_step_info"
    const val EXTRA_TASK_NAME = "extra_task_name"
    const val EXTRA_TASK_STATUS = "extra_task_status"
    const val EXTRA_ERROR_MESSAGE = "extra_error_message"
    const val EXTRA_DUMP_XML = "extra_dump_xml"
    const val EXTRA_OLD_STATE = "extra_old_state"
    const val EXTRA_NEW_STATE = "extra_new_state"
    const val EXTRA_RISK_TYPE = "extra_risk_type"
    const val EXTRA_RISK_DETAIL = "extra_risk_detail"
    const val EXTRA_GREETING_TEXT = "extra_greeting_text"

    // ==================== Task 回执状态枚举值 ====================
    const val STATUS_SUCCESS = "STATUS_SUCCESS"
    const val STATUS_FAILED = "STATUS_FAILED"
    const val STATUS_TIMEOUT = "STATUS_TIMEOUT"
}
