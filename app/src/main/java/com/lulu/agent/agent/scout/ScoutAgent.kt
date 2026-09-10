package com.lulu.agent.agent.scout

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.lulu.agent.accessibility.BossAccessibilityService
import com.lulu.agent.accessibility.model.ScrapedRawJob
import com.lulu.agent.accessibility.task.InspectDetailTask
import com.lulu.agent.accessibility.task.ScrollFeedTask
import com.lulu.agent.accessibility.util.AccessibilityNodeUtil
import com.lulu.agent.agent.base.BaseAgent
import java.util.concurrent.atomic.AtomicBoolean

class ScoutAgent(context: Context) : BaseAgent(context, "ScoutAgent") {

    data class CardAnchor(
        val bounds: Rect,
        val previewTitle: String,
        val previewCompany: String
    )

    private data class LeafTextNode(
        val viewId: String,
        val text: String,
        val bounds: Rect
    )

    companion object {
        private const val BOSS_PKG = "com.hpbr.bosszhipin"
        private const val ID_FEED_LIST = "com.hpbr.bosszhipin:id/rv_list"
        private val hasDumpedFirstCard = AtomicBoolean(false)

        // 薪资正则特征
        private val SALARY_REGEX = Regex("""(\d+[-~至]\d+[Kk万·元]|面议|\d+元/[天月小时])""")
        // 常见干扰标签特征（年限、学历、HR等）
        private val TAG_FILTER_REGEX = Regex("""(经验|年|应届|本科|大专|硕士|博士|学历|不限|在校|先生|女士|HR|招聘|活跃|刚刚|今日)""")
    }

    /**
     * 核心扫描入口：带结构现场打印与语义降级识别
     */
    fun scanCurrentFeedCards(): List<CardAnchor> {
        val service = BossAccessibilityService.instance ?: return emptyList()
        val targetRoot = getBossAppRoot(service) ?: return emptyList()
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val anchors = mutableListOf<CardAnchor>()

        try {
            val cardNodes = targetRoot.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/cl_card_container")
            if (cardNodes.isNullOrEmpty()) {
                Log.w(tag, "未找到 cl_card_container 节点")
                return emptyList()
            }

            Log.d(tag, "命中卡片容器，开始逐张深度解析，共 ${cardNodes.size} 个节点")
            val rect = Rect()

            for ((index, card) in cardNodes.withIndex()) {
                card.getBoundsInScreen(rect)

                // 排除高度过小或不在安全视口内的卡片
                if (rect.height() < 120 || !AccessibilityNodeUtil.isCenterInBounds(card, safeBounds)) {
                    card.recycle()
                    continue
                }

                // 提取卡片内所有带有文字的叶子节点
                val leafNodes = mutableListOf<LeafTextNode>()
                collectLeafTextNodes(card, leafNodes)

                // 第一次扫描时，向 Logcat 打印第一张卡片的完整内部 DOM，便于现场诊断
                if (hasDumpedFirstCard.compareAndSet(false, true)) {
                    dumpCardInternals(leafNodes)
                }

                // 解析职位名与公司名（融合 ID 模糊检索 + 布局几何语义检索）
                val (title, company) = resolveTitleAndCompany(leafNodes, rect)

                if (title.isNotBlank() && company.isNotBlank()) {
                    anchors.add(CardAnchor(Rect(rect), title, company))
                    Log.i(tag, "✅ 成功捕获卡片[$index] -> 职位:【$title】 公司:【$company】 坐标: $rect")
                } else {
                    Log.w(tag, "⚠️ 解析不全被跳过 -> Title: '$title', Company: '$company', 文字列表: ${leafNodes.map { it.text }}")
                }

                card.recycle()
            }

        } finally {
            targetRoot.recycle()
        }

        Log.d(tag, "当前屏幕捕获有效岗位卡片数: ${anchors.size}")
        return anchors
    }

    /**
     * 递归收集卡片下所有带有实际文字（Text 或 ContentDescription）的节点
     */
    private fun collectLeafTextNodes(node: AccessibilityNodeInfo?, result: MutableList<LeafTextNode>) {
        if (node == null) return

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val actualText = if (text.isNotEmpty()) text else desc
        val viewId = node.viewIdResourceName ?: ""

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // 仅收录包含实际文字且不是整个大容器的节点
        if (actualText.isNotEmpty() && actualText.length < 50) {
            result.add(LeafTextNode(viewId, actualText, bounds))
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectLeafTextNodes(child, result)
            child.recycle()
        }
    }

    /**
     * 双轨解析核心：同时兼容 ID 匹配与视觉空间语义
     */
    private fun resolveTitleAndCompany(
        leafs: List<LeafTextNode>,
        cardBounds: Rect
    ): Pair<String, String> {
        var resolvedTitle = ""
        var resolvedCompany = ""

        // 轨道 1：优先尝试通过 ViewID 的关键词模糊匹配
        for (leaf in leafs) {
            val id = leaf.viewId.lowercase()
            if (resolvedTitle.isEmpty() && (id.contains("position") || id.contains("job_name") || id.contains("title"))) {
                resolvedTitle = leaf.text
            }
            if (resolvedCompany.isEmpty() && (id.contains("company") || id.contains("brand") || id.contains("com_name"))) {
                resolvedCompany = leaf.text
            }
        }

        // 轨道 2：若 ID 提取落空，直接启用无懈可击的【视觉几何排版语义】
        if (resolvedTitle.isEmpty() || resolvedCompany.isEmpty()) {
            // 按从上到下的纵坐标排序
            val sortedByY = leafs.sortedBy { it.bounds.top }

            // A. 定位 Title：卡片上半区（前 40% 高度内），排除掉薪资文本之后的第一个非空文本
            if (resolvedTitle.isEmpty()) {
                val upperCutoffY = cardBounds.top + (cardBounds.height() * 0.42f).toInt()
                val topCandidates = sortedByY.filter { it.bounds.top <= upperCutoffY }

                for (item in topCandidates) {
                    // 薪资特征文本不能作为 Title
                    if (!SALARY_REGEX.containsMatchIn(item.text)) {
                        resolvedTitle = item.text
                        break
                    }
                }
            }

            // B. 定位 Company：卡片中下部区域，排除常规标签、薪资、HR 关键字之后的独立实体名称
            if (resolvedCompany.isEmpty()) {
                val lowerCutoffY = cardBounds.top + (cardBounds.height() * 0.35f).toInt()
                val bottomCandidates = sortedByY.filter { it.bounds.top > lowerCutoffY }

                for (item in bottomCandidates) {
                    val txt = item.text
                    // 排除薪资、排除职位自身、排除学历年限等标签
                    if (txt != resolvedTitle &&
                        !SALARY_REGEX.containsMatchIn(txt) &&
                        !TAG_FILTER_REGEX.containsMatchIn(txt) &&
                        txt.length >= 2
                    ) {
                        resolvedCompany = txt
                        break
                    }
                }
            }
        }

        return Pair(resolvedTitle, resolvedCompany)
    }

    /**
     * 诊断用：转储第一张卡片的结构到日志
     */
    private fun dumpCardInternals(leafs: List<LeafTextNode>) {
        val sb = StringBuilder("\n===== 🎯 侦察兵实地采样：首张岗位卡片内部文字结构 =====\n")
        leafs.forEachIndexed { i, leaf ->
            sb.append("[$i] id='${leaf.viewId}', text='${leaf.text}', bounds=${leaf.bounds.toShortString()}\n")
        }
        sb.append("==========================================================")
        Log.i(tag, sb.toString())
    }

    /**
     * 获取 Boss 直聘主窗口 Root
     */
    private fun getBossAppRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        val defaultRoot = service.rootInActiveWindow
        if (defaultRoot != null && defaultRoot.packageName == BOSS_PKG) {
            return defaultRoot
        }
        defaultRoot?.recycle()

        val windows = service.windows
        for (win in windows) {
            if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = win.root
                if (root != null && root.packageName == BOSS_PKG) {
                    return root
                }
                root?.recycle()
            }
        }
        return null
    }

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
                val cleanedJd = JDContentCleaner.clean(rawJob.jobDescription)
                val finalJob = rawJob.copy(jobDescription = cleanedJd)
                onSuccess(finalJob)
            },
            onError = onFailed // 🌟【新增透传错误回调】
        )
        service.executeTask(task)
    }

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
        val task = ScrollFeedTask(
            allowTabSwitch = allowTabSwitch,
            onCompleted = onCompleted, // 🌟【新增透传成功回调】
            onFailed = onFailed        // 🌟【新增透传失败回调】
        )
        service.executeTask(task)
    }
}