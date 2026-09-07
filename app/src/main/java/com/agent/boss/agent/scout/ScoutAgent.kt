package com.agent.boss.agent.scout

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.agent.boss.accessibility.BossAccessibilityService
import com.agent.boss.accessibility.model.ScrapedRawJob
import com.agent.boss.accessibility.task.InspectDetailTask
import com.agent.boss.accessibility.task.ScrollFeedTask
import com.agent.boss.accessibility.task.TaskCallback
import com.agent.boss.accessibility.util.AccessibilityNodeUtil
import com.agent.boss.agent.base.BaseAgent

/**
 * 感知侦察兵：负责页面卡片扫描、JD采集与滑动翻页调度
 */
class ScoutAgent(context: Context) : BaseAgent(context, "ScoutAgent") {

    data class CardAnchor(
        val bounds: Rect,
        val previewTitle: String,
        val previewCompany: String
    )

    companion object {
        private const val ID_CARD_CONTAINER = "com.hpbr.bosszhipin:id/cl_card_container"
        private const val ID_POSITION_NAME = "com.hpbr.bosszhipin:id/tv_position_name"
        private const val ID_COMPANY_NAME = "com.hpbr.bosszhipin:id/tv_company_name"
    }

    override fun start() {
        super.start()
        sendAgentLog("🔭 侦察工兵已上线，准备巡查岗位信息流")
    }

    /**
     * 抓取当前屏幕可见的所有职位卡片锚点（含边界与简单预览信息）
     */
    fun scanCurrentFeedCards(): List<CardAnchor> {
        val service = BossAccessibilityService.instance ?: return emptyList()
        val root = service.rootInActiveWindow ?: return emptyList()
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)

        val cardNodes = root.findAccessibilityNodeInfosByViewId(ID_CARD_CONTAINER)
        if (cardNodes.isNullOrEmpty()) {
            root.recycle()
            return emptyList()
        }

        val anchors = mutableListOf<CardAnchor>()
        val rect = Rect()

        for (node in cardNodes) {
            node.getBoundsInScreen(rect)
            // 确保卡片中心点在安全屏幕区域内（排除上下被状态栏或导航栏切断一半的卡片）
            if (AccessibilityNodeUtil.isCenterInBounds(node, safeBounds)) {
                val titleNodes = node.findAccessibilityNodeInfosByViewId(ID_POSITION_NAME)
                val compNodes = node.findAccessibilityNodeInfosByViewId(ID_COMPANY_NAME)

                val title = titleNodes?.firstOrNull()?.text?.toString() ?: ""
                val comp = compNodes?.firstOrNull()?.text?.toString() ?: ""

                titleNodes?.forEach { it.recycle() }
                compNodes?.forEach { it.recycle() }

                if (title.isNotBlank() && comp.isNotBlank()) {
                    anchors.add(CardAnchor(Rect(rect), title, comp))
                }
            }
            node.recycle()
        }

        root.recycle()
        Log.d(tag, "当前屏幕捕获有效岗位卡片数: ${anchors.size}")
        return anchors
    }

    /**
     * 调度进入详情页并抓取洗净后的全量 JD
     */
    fun inspectJobDetail(
        cardBounds: Rect,
        onSuccess: (ScrapedRawJob) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val service = BossAccessibilityService.instance
        if (service == null) {
            onFailed("无障碍服务未连接")
            return
        }

        sendAgentLog("进入目标岗位详情并提取完整 JD...")
        val task = InspectDetailTask(
            cardClickBounds = cardBounds,
            onJobScraped = { rawJob ->
                // 洗净纯文本 JD
                val cleanedJd = JDContentCleaner.clean(rawJob.jobDescription)
                val finalJob = rawJob.copy(jobDescription = cleanedJd)
                onSuccess(finalJob)
            }
        )

        service.executeTask(task)
    }

    /**
     * 调度向上滑动翻页
     */
    fun scrollNextPage(
        allowTabSwitch: Boolean = true,
        onCompleted: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val service = BossAccessibilityService.instance
        if (service == null) {
            onFailed("无障碍服务未连接")
            return
        }

        sendAgentLog("当前屏幕已检索完毕，拟人化上滑加载新岗位...")
        val task = ScrollFeedTask(allowTabSwitch = allowTabSwitch)
        service.executeTask(task)
    }
}
