package com.agent.boss.dispatcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.agent.boss.accessibility.model.PageScene
import com.agent.boss.dispatcher.contract.DispatcherBroadcasts

/**
 * 无障碍底层感知与任务反馈接收器
 */
class AccessibilityFeedbackReceiver : BroadcastReceiver() {

    var onSceneChanged: ((PageScene) -> Unit)? = null
    var onTaskFeedback: ((taskName: String, isSuccess: Boolean, errorMsg: String) -> Unit)? = null
    var onStepMessage: ((taskName: String, message: String) -> Unit)? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            DispatcherBroadcasts.ACTION_PAGE_SCENE_CHANGED -> {
                val sceneName = intent.getStringExtra(DispatcherBroadcasts.EXTRA_SCENE_NAME) ?: return
                try {
                    val scene = PageScene.valueOf(sceneName)
                    onSceneChanged?.invoke(scene)
                } catch (e: Exception) {
                    onSceneChanged?.invoke(PageScene.UNKNOWN)
                }
            }

            DispatcherBroadcasts.ACTION_TASK_FEEDBACK -> {
                val taskName = intent.getStringExtra(DispatcherBroadcasts.EXTRA_TASK_NAME) ?: ""
                val status = intent.getStringExtra(DispatcherBroadcasts.EXTRA_TASK_STATUS) ?: ""
                val errorMsg = intent.getStringExtra(DispatcherBroadcasts.EXTRA_ERROR_MESSAGE) ?: ""
                val isSuccess = status == DispatcherBroadcasts.STATUS_SUCCESS
                onTaskFeedback?.invoke(taskName, isSuccess, errorMsg)
            }

            DispatcherBroadcasts.ACTION_TASK_STEP_MESSAGE -> {
                val taskName = intent.getStringExtra(DispatcherBroadcasts.EXTRA_TASK_NAME) ?: ""
                val msg = intent.getStringExtra(DispatcherBroadcasts.EXTRA_STEP_INFO) ?: ""
                onStepMessage?.invoke(taskName, msg)
            }
        }
    }

    companion object {
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(DispatcherBroadcasts.ACTION_PAGE_SCENE_CHANGED)
                addAction(DispatcherBroadcasts.ACTION_TASK_FEEDBACK)
                addAction(DispatcherBroadcasts.ACTION_TASK_STEP_MESSAGE)
            }
        }
    }
}
