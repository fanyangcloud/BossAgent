package com.agent.boss.accessibility.task

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.boss.accessibility.model.ScrapedRawJob
import com.agent.boss.accessibility.util.GestureEngine

/**
 * 岗位详情巡查任务 (纯净版：只负责进入详情并抓取 JD，抓完保持停留在详情页，等待 Agent 决策)
 */
class InspectDetailTask(
    private val cardClickBounds: Rect? = null,
    private val onJobScraped: ((ScrapedRawJob) -> Unit)? = null
) : BaseAutomationTask() {

    private val tag = "InspectDetailTask"

    companion object {
        private const val STEP_CLICK_CARD = 1
        private const val STEP_VERIFY_DETAIL_PAGE = 2
        private const val STEP_EXTRACT_DATA = 3

        private const val ID_JOB_NAME = "com.hpbr.bosszhipin:id/tv_job_name"
        private const val ID_JOB_SALARY = "com.hpbr.bosszhipin:id/tv_job_salary"
        private const val ID_LOCATION = "com.hpbr.bosszhipin:id/tv_required_location"
        private const val ID_BOSS_NAME = "com.hpbr.bosszhipin:id/tv_boss_name"
        private const val ID_BOSS_TITLE = "com.hpbr.bosszhipin:id/tv_boss_title"
        private const val ID_BOSS_LABEL = "com.hpbr.bosszhipin:id/boss_label_tv"
        private const val ID_DESCRIPTION = "com.hpbr.bosszhipin:id/tv_description"
        private const val ID_COMPANY_NAME = "com.hpbr.bosszhipin:id/tv_com_name"
        private const val ID_CHAT_BUTTON = "com.hpbr.bosszhipin:id/btn_chat"
    }

    override fun onStep(step: Int) {
        when (step) {
            STEP_CLICK_CARD -> handleClickCard()
            STEP_VERIFY_DETAIL_PAGE -> handleVerifyDetailPage()
            STEP_EXTRACT_DATA -> handleExtractData()
        }
    }

    private fun handleClickCard() {
        if (cardClickBounds == null) {
            sendStep(STEP_VERIFY_DETAIL_PAGE, 100L)
            return
        }

        sendTaskMessage("点击目标岗位卡片进入详情...")
        val clicked = GestureEngine.clickAt(
            service = service,
            x = cardClickBounds.centerX(),
            y = cardClickBounds.centerY(),
            onSuccess = { nextStep(900L) }, // 留足 900ms 保证详情转场动画结束
            onFail = { retryCurrentStep(400L, "点击卡片失败") }
        )

        if (!clicked) {
            retryCurrentStep(500L, "无法派发卡片点击手势")
        }
    }

    private fun handleVerifyDetailPage() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "等待详情页渲染")
            return
        }

        val hasChatBtn = root.findAccessibilityNodeInfosByViewId(ID_CHAT_BUTTON).isNotEmpty()
        val hasJobName = root.findAccessibilityNodeInfosByViewId(ID_JOB_NAME).isNotEmpty()
        root.recycle()

        if (hasChatBtn || hasJobName) {
            Log.d(tag, "成功定位岗位详情页")
            nextStep(200L)
        } else {
            retryCurrentStep(500L, "尚未检测到详情页特征元素")
        }
    }

    private fun handleExtractData() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "提取数据时根节点为空")
            return
        }

        val jobName = safeExtractText(root, ID_JOB_NAME)
        val jobSalary = safeExtractText(root, ID_JOB_SALARY)
        val location = safeExtractText(root, ID_LOCATION)
        val bossName = safeExtractText(root, ID_BOSS_NAME)
        val bossTitle = safeExtractText(root, ID_BOSS_TITLE)
        val bossLabel = safeExtractText(root, ID_BOSS_LABEL)
        val description = safeExtractText(root, ID_DESCRIPTION)
        val companyName = safeExtractText(root, ID_COMPANY_NAME)
        root.recycle()

        if (jobName.isEmpty() && description.isEmpty()) {
            retryCurrentStep(500L, "提取到的岗位名与描述皆为空")
            return
        }

        val job = ScrapedRawJob(
            title = jobName,
            companyName = companyName,
            salaryText = jobSalary,
            city = location,
            hrName = bossName,
            hrTitle = bossTitle,
            hrActiveStatus = bossLabel,
            jobDescription = description,
            timestamp = System.currentTimeMillis()
        )

        sendTaskMessage("📑 成功采集【$companyName - $jobName】，JD 长度: ${description.length} 字")
        onJobScraped?.invoke(job)

        // 核心改动：采集完成即大功告成，停留在详情页，绝不主动按返回键！
        finishTask()
    }

    private fun safeExtractText(root: AccessibilityNodeInfo, viewId: String): String {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return ""
        if (nodes.isEmpty()) return ""
        val text = nodes[0].text?.toString()?.trim() ?: ""
        nodes.forEach { it.recycle() }
        return text
    }
}