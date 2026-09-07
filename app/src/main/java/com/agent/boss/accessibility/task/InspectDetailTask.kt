package com.agent.boss.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.boss.accessibility.model.ScrapedRawJob
import com.agent.boss.accessibility.util.AccessibilityNodeUtil
import com.agent.boss.accessibility.util.GestureEngine

/**
 * 岗位详情巡查与全量 JD 抓取任务
 *
 * 执行流程：
 * 1. (可选) 点击信息流中的卡片物理坐标进入详情页
 * 2. 校验详情页核心元素 (tv_job_name / tv_description)
 * 3. 一次性提取结构化数据 (职位名、薪资、公司全称、HR状态、全量JD)
 * 4. 点击 iv_back 或调用系统全局返回，安全回退至列表流
 */
class InspectDetailTask(
    private val cardClickBounds: Rect? = null,
    private val onJobScraped: ((ScrapedRawJob) -> Unit)? = null
) : BaseAutomationTask() {

    private val tag = "InspectDetailTask"
    private var extractedJob: ScrapedRawJob? = null

    companion object {
        private const val STEP_CLICK_CARD = 1
        private const val STEP_VERIFY_DETAIL_PAGE = 2
        private const val STEP_EXTRACT_DATA = 3
        private const val STEP_NAVIGATE_BACK = 4
        private const val STEP_VERIFY_BACK_SETTLE = 5

        // XML 中精准提取的真实 Resource ID
        private const val ID_BACK_BUTTON = "com.hpbr.bosszhipin:id/iv_back"
        private const val ID_JOB_NAME = "com.hpbr.bosszhipin:id/tv_job_name"
        private const val ID_JOB_SALARY = "com.hpbr.bosszhipin:id/tv_job_salary"
        private const val ID_LOCATION = "com.hpbr.bosszhipin:id/tv_required_location"
        private const val ID_BOSS_NAME = "com.hpbr.bosszhipin:id/tv_boss_name"
        private const val ID_BOSS_TITLE = "com.hpbr.bosszhipin:id/tv_boss_title"
        private const val ID_BOSS_LABEL = "com.hpbr.bosszhipin:id/boss_label_tv"
        private const val ID_DESCRIPTION = "com.hpbr.bosszhipin:id/tv_description"
        private const val ID_COMPANY_NAME = "com.hpbr.bosszhipin:id/tv_com_name"
        private const val ID_COMPANY_INFO = "com.hpbr.bosszhipin:id/tv_com_info"
        private const val ID_CHAT_BUTTON = "com.hpbr.bosszhipin:id/btn_chat"
    }

    override fun onStep(step: Int) {
        when (step) {
            STEP_CLICK_CARD -> handleClickCard()
            STEP_VERIFY_DETAIL_PAGE -> handleVerifyDetailPage()
            STEP_EXTRACT_DATA -> handleExtractData()
            STEP_NAVIGATE_BACK -> handleNavigateBack()
            STEP_VERIFY_BACK_SETTLE -> handleVerifyBackSettle()
        }
    }

    /**
     * 步骤 1：若传入了卡片边界，执行拟人化物理坐标点击进入详情
     */
    private fun handleClickCard() {
        if (cardClickBounds == null) {
            // 已在详情页内部，直接跳至验证步骤
            sendStep(STEP_VERIFY_DETAIL_PAGE, 100L)
            return
        }

        sendTaskMessage("点击目标岗位卡片进入详情...")
        val clicked = GestureEngine.clickAt(
            service = service,
            x = cardClickBounds.centerX(),
            y = cardClickBounds.centerY(),
            onSuccess = {
                // 等待 850ms 让转场动画与网络数据就绪
                nextStep(850L)
            },
            onFail = {
                retryCurrentStep(400L, "点击卡片进入详情失败")
            }
        )

        if (!clicked) {
            retryCurrentStep(500L, "无法派发卡片点击手势")
        }
    }

    /**
     * 步骤 2：校验是否成功抵达职位详情页
     */
    private fun handleVerifyDetailPage() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(500L, "等待详情页渲染")
            return
        }

        val hasChatBtn = root.findAccessibilityNodeInfosByViewId(ID_CHAT_BUTTON).isNotEmpty()
        val hasJobName = root.findAccessibilityNodeInfosByViewId(ID_JOB_NAME).isNotEmpty()

        root.recycle()

        if (hasChatBtn || hasJobName) {
            Log.d(tag, "成功定位岗位详情页")
            nextStep(250L)
        } else {
            retryCurrentStep(600L, "尚未检测到详情页特征元素")
        }
    }

    /**
     * 步骤 3：全量提取结构化 JD 与企业数据
     */
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

        extractedJob = ScrapedRawJob(
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
        Log.i(tag, "抓取成功: $extractedJob")

        // 回调给上层 Agent
        extractedJob?.let { onJobScraped?.invoke(it) }

        nextStep(300L)
    }

    /**
     * 步骤 4：点击返回按钮离开详情页
     */
    private fun handleNavigateBack() {
        sendTaskMessage("准备返回列表流...")

        val root = service.rootInActiveWindow
        val backNodes = root?.findAccessibilityNodeInfosByViewId(ID_BACK_BUTTON)
        val backNode = backNodes?.firstOrNull()

        // 优先使用真实坐标手势点击左上角返回按钮
        if (backNode != null) {
            GestureEngine.performClick(
                service = service,
                node = backNode,
                onSuccess = {
                    backNodes.forEach { it.recycle() }
                    root.recycle()
                    nextStep(650L)
                },
                onFail = {
                    backNodes.forEach { it.recycle() }
                    root.recycle()
                    fallbackGlobalBack()
                }
            )
        } else {
            root?.recycle()
            fallbackGlobalBack()
        }
    }

    private fun fallbackGlobalBack() {
        Log.w(tag, "未定位到返回键，触发系统全局返回动作")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        nextStep(650L)
    }

    /**
     * 步骤 5：等待列表流稳定后闭环任务
     */
    private fun handleVerifyBackSettle() {
        sendTaskMessage("已安全返回信息流")
        finishTask()
    }

    /**
     * 安全提取单字段文本并即时回收全部节点（防止 DFS 内存泄漏）
     */
    private fun safeExtractText(root: AccessibilityNodeInfo, viewId: String): String {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return ""
        if (nodes.isEmpty()) return ""

        val firstNode = nodes[0]
        val extractedText = firstNode.text?.toString()?.trim() ?: ""

        // 回收系统返回的全部节点
        for (node in nodes) {
            node.recycle()
        }
        return extractedText
    }
}
