package com.lulu.agent.accessibility.task

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.lulu.agent.accessibility.util.AccessibilityNodeUtil
import com.lulu.agent.accessibility.util.GestureEngine
import java.util.concurrent.ThreadLocalRandom

/**
 * 职位信息流滑动浏览任务
 *
 * 核心特性：
 * 1. 拟人化向上滑动浏览，随机起止坐标与滑动耗时。
 * 2. 概率性轮换“推荐”与“最新”两类职位源（约 20% 概率触发探查，避免只刷推荐流导致的信息茧房）。
 * 3. 规避 Boss 直聘大量 TextView clickable=false 的限制，全面使用物理坐标手势点击。
 */
class ScrollFeedTask(
    private val allowTabSwitch: Boolean = true,
    private val onCompleted: (() -> Unit)? = null, // 🌟【新增完成回调】
    private val onFailed: ((String) -> Unit)? = null  // 🌟【新增失败回调】
) : BaseAutomationTask() {

    private val tag = "ScrollFeedTask"

    companion object {
        private const val STEP_CHECK_AND_SWITCH_TAB = 1
        private const val STEP_PERFORM_SWIPE = 2
        private const val STEP_SETTLE_AND_FINISH = 3

        private const val TAB_RECOMMEND = "推荐"
        private const val TAB_LATEST = "最新"

        private const val ID_RECYCLER_VIEW = "com.hpbr.bosszhipin:id/rv_list"
        private const val ID_TAB_LABEL = "com.hpbr.bosszhipin:id/tv_tab_label"
    }

    override fun onStep(step: Int) {
        when (step) {
            STEP_CHECK_AND_SWITCH_TAB -> handleTabCheckAndSwitch()
            STEP_PERFORM_SWIPE -> handlePerformSwipe()
            STEP_SETTLE_AND_FINISH -> handleSettleAndFinish()
        }
    }

    /**
     * 步骤 1：校验页面列表，并以一定概率在“推荐”和“最新”之间切换
     */
    private fun handleTabCheckAndSwitch() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "无法获取窗口根节点")
            return
        }

        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)

        // 校验是否存在职位列表
        val rvNode = AccessibilityNodeUtil.findNodeById(service, ID_RECYCLER_VIEW)
        if (rvNode == null) {
            root.recycle()
            retryCurrentStep(600L, "未找到职位列表容器")
            return
        }
        rvNode.recycle()

        // 如果未开启 Tab 轮转策略，直接执行滑动
        if (!allowTabSwitch) {
            root.recycle()
            sendStep(STEP_PERFORM_SWIPE, 150L)
            return
        }

        // 概率判定：约 20% 概率切换到对立 Tab
        val shouldSwitchTab = ThreadLocalRandom.current().nextInt(100) < 20
        if (!shouldSwitchTab) {
            root.recycle()
            sendStep(STEP_PERFORM_SWIPE, 150L)
            return
        }

        // 查找“推荐”与“最新”Tab 节点
        val recommendNode = findTabNode(root, safeBounds, TAB_RECOMMEND)
        val latestNode = findTabNode(root, safeBounds, TAB_LATEST)

        if (recommendNode != null && latestNode != null) {
            val isRecommendSelected = recommendNode.isSelected
            val isLatestSelected = latestNode.isSelected

            val targetNode = when {
                isRecommendSelected -> latestNode // 当前在推荐 -> 切到最新
                isLatestSelected -> recommendNode    // 当前在最新 -> 切到推荐
                else -> null
            }

            if (targetNode != null) {
                val targetName = targetNode.text?.toString() ?: "目标Tab"
                sendTaskMessage("🎲 触发策略：模拟手势切换到【$targetName】流")

                // 重点：使用物理坐标手势点击（无视 clickable=false）
                val clickSuccess = GestureEngine.performClick(
                    service = service,
                    node = targetNode,
                    onSuccess = {
                        Log.i(tag, "成功点击切换 Tab: $targetName")
                    },
                    onFail = {
                        Log.w(tag, "点击切换 Tab 失败")
                    }
                )

                recommendNode.recycle()
                latestNode.recycle()
                root.recycle()

                if (clickSuccess) {
                    // 切换 Tab 后等待 1200ms 让列表刷新完成
                    nextStep(1200L)
                    return
                }
            } else {
                recommendNode.recycle()
                latestNode.recycle()
            }
        } else {
            recommendNode?.recycle()
            latestNode?.recycle()
        }

        root.recycle()
        // 未执行切换，直接进入滑动步骤
        sendStep(STEP_PERFORM_SWIPE, 150L)
    }

    /**
     * 步骤 2：执行拟人化向上滑动
     */
    private fun handlePerformSwipe() {
        sendTaskMessage("正在执行拟人化翻页滑动...")

        // 生成 650ms ~ 950ms 的自然拖动时长
        val randomDuration = ThreadLocalRandom.current().nextLong(650, 950)

        val swipeQueued = GestureEngine.swipeUp(
            service = service,
            durationMs = randomDuration,
            onSuccess = {
                Log.d(tag, "向上滑动完成")
                // 滑动惯性停止后，留出 500ms 缓冲等待视觉卡片停止移动
                sendStep(STEP_SETTLE_AND_FINISH, 500L)
            },
            onFail = {
                Log.e(tag, "向上滑动被系统取消或失败")
                retryCurrentStep(400L, "滑动手势失败")
            }
        )

        if (!swipeQueued) {
            retryCurrentStep(500L, "滑动手势无法派发")
        }
    }

    /**
     * 步骤 3：滑动稳定后完成任务
     */
    private fun handleSettleAndFinish() {
        sendTaskMessage("翻页完成，已加载新一屏职位")
        finishTask()
    }

    /**
     * 辅助：在安全屏幕边界内定位指定文本的 Tab 节点
     */
    private fun findTabNode(
        root: AccessibilityNodeInfo,
        screenBounds: Rect,
        tabText: String
    ): AccessibilityNodeInfo? {
        val tabNodes = root.findAccessibilityNodeInfosByViewId(ID_TAB_LABEL)
        if (tabNodes.isNullOrEmpty()) return null

        var target: AccessibilityNodeInfo? = null
        for (node in tabNodes) {
            if (target == null &&
                node.text?.toString() == tabText &&
                AccessibilityNodeUtil.isCenterInBounds(node, screenBounds)
            ) {
                target = node
                continue
            }
            node.recycle()
        }
        return target
    }


    // 🌟【新增：完成时唤醒 Deferred】
    override fun finishTask() {
        super.finishTask()
        onCompleted?.invoke()
    }

    // 🌟【新增：失败时唤醒 Deferred】
    override fun failedTask(reason: String) {
        super.failedTask(reason)
        onFailed?.invoke(reason)
    }

    // 🌟【新增：强杀时释放 Deferred】
    override fun onForceStopped() {
        onFailed?.invoke("任务被强制终止")
    }
}
