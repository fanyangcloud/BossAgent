package com.agent.boss.accessibility.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

/**
 * 无障碍节点高可靠检索与生命周期管理工具类 (重构优化版)
 *
 * 核心优化：
 * 1. 严格遵循深度优先遍历(DFS)过程中的节点主动 recycle 机制，杜绝 4G/8G 运存机型内存泄漏。
 * 2. 预编译清洗正则，抹除 Emoji、标点、空白符，支持高容错的模糊匹配。
 * 3. 屏幕边界自动扣除顶部状态栏高危区域，只查找真正可视且可操作的节点。
 * 4. 提供 Kotlin DSL 扩展，支持简洁直观的内联谓词查询。
 */
object AccessibilityNodeUtil {

    private const val TAG = "AccessibilityNodeUtil"

    /**
     * 清洗正则：匹配各类标点、特殊符号、Emoji、数学符号、空白与分隔符
     */
    private val CLEAN_PATTERN: Pattern = Pattern.compile("[\\p{P}\\p{S}\\p{Z}\\s]+")

    /**
     * 清洗文本，便于做强鲁棒性对比
     */
    fun cleanString(input: CharSequence?): String {
        if (input.isNullOrEmpty()) return ""
        return CLEAN_PATTERN.matcher(input).replaceAll("")
    }

    // ==================== 屏幕物理与安全边界计算 ====================

    /**
     * 获取屏幕真实物理分辨率
     */
    fun getScreenBounds(context: Context?): Rect {
        if (context == null) return Rect()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return Rect()
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /**
     * 获取排除了状态栏的安全操作区域 Rect(0, statusBarHeight, screenWidth, screenHeight)
     */
    fun getSafeScreenBounds(context: Context?): Rect {
        val bounds = getScreenBounds(context)
        if (bounds.isEmpty) return bounds
        val statusBarHeight = getStatusBarHeight(context)
        return Rect(0, statusBarHeight, bounds.width(), bounds.height())
    }

    /**
     * 获取系统状态栏高度 (默认兜底 60px)
     */
    fun getStatusBarHeight(context: Context?): Int {
        if (context == null) return 60
        return try {
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 60
        } catch (e: Exception) {
            60
        }
    }

    /**
     * 校验节点中心点是否完全落在指定边界矩形内
     */
    fun isCenterInBounds(node: AccessibilityNodeInfo?, targetBounds: Rect): Boolean {
        if (node == null || targetBounds.isEmpty) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val cx = rect.centerX()
        val cy = rect.centerY()
        return targetBounds.contains(cx, cy)
    }

    // ==================== 经典节点定位方法 (含深度回收保护) ====================

    /**
     * 根据文本查找屏幕安全范围内的第一个节点 (包含匹配或全等)
     */
    fun findNodeByText(
        service: AccessibilityService?,
        text: String,
        exactMatch: Boolean = false
    ): AccessibilityNodeInfo? {
        if (service == null || text.isEmpty()) return null
        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)

        val nodes = root.findAccessibilityNodeInfosByText(text)
        val result = findFirstInBoundsAndRecycleRest(nodes, safeBounds) { node ->
            val nodeText = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            if (exactMatch) {
                nodeText == text || desc == text
            } else {
                nodeText.contains(text) || desc.contains(text)
            }
        }

        if (root != result) {
            root.recycle()
        }
        return result
    }

    /**
     * 忽略空格、标点、Emoji 的清洗模糊查找
     */
    fun findNodeByTextCleaned(service: AccessibilityService?, text: String): AccessibilityNodeInfo? {
        if (service == null || text.isEmpty()) return null
        val targetCleaned = cleanString(text)
        if (targetCleaned.isEmpty()) return null

        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)

        val result = dfsFindNode(root, safeBounds) { node ->
            val nodeCleaned = cleanString(node.text)
            val descCleaned = cleanString(node.contentDescription)
            nodeCleaned.contains(targetCleaned) || descCleaned.contains(targetCleaned)
        }

        if (root != result) {
            root.recycle()
        }
        return result
    }

    /**
     * 根据 Resource ID 查找在屏幕安全区域内的节点
     */
    fun findNodeById(service: AccessibilityService?, viewId: String): AccessibilityNodeInfo? {
        if (service == null || viewId.isEmpty()) return null
        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)

        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val result = findFirstInBoundsAndRecycleRest(nodes, safeBounds)

        if (root != result) {
            root.recycle()
        }
        return result
    }

    /**
     * 根据 ContentDescription 查找节点 (支持前缀匹配或包含匹配)
     */
    fun findNodeByDescription(
        service: AccessibilityService?,
        prefixOrDesc: String,
        startsWith: Boolean = false
    ): AccessibilityNodeInfo? {
        if (service == null || prefixOrDesc.isEmpty()) return null
        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)

        val result = dfsFindNode(root, safeBounds) { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            if (startsWith) desc.startsWith(prefixOrDesc) else desc.contains(prefixOrDesc)
        }

        if (root != result) {
            root.recycle()
        }
        return result
    }

    /**
     * 根据类名和文本联合查找，支持指定横向水平起止比例 (例如查屏幕右半侧的特定按钮)
     */
    fun findNodeByClassAndText(
        service: AccessibilityService?,
        className: String,
        text: String,
        minRatioX: Float = 0.0f,
        maxRatioX: Float = 1.0f
    ): AccessibilityNodeInfo? {
        if (service == null || className.isEmpty() || text.isEmpty()) return null
        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)

        val minX = (safeBounds.width() * minRatioX).toInt()
        val maxX = (safeBounds.width() * maxRatioX).toInt()

        val result = dfsFindNode(root, safeBounds) { node ->
            val classMatched = className == node.className?.toString()
            val textMatched = text == node.text?.toString()?.trim()
            if (classMatched && textMatched) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.left >= minX && bounds.right <= maxX
            } else {
                false
            }
        }

        if (root != result) {
            root.recycle()
        }
        return result
    }

    // ==================== DFS 递归引擎与即时回收算法 ====================

    /**
     * DFS 深度优先搜索符合条件的节点，非结果节点全部即时 recycle
     */
    fun dfsFindNode(
        node: AccessibilityNodeInfo?,
        targetBounds: Rect,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null

        // 1. 判定当前节点本身是否满足条件且在屏幕内
        if (isCenterInBounds(node, targetBounds) && predicate(node)) {
            return node
        }

        // 2. 遍历并递归子节点
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val result = dfsFindNode(child, targetBounds, predicate)
            if (result != null) {
                // 如果结果不是当前这一层的 child，当前 child 作为路径节点可以释放
                if (result != child) {
                    child.recycle()
                }
                return result
            } else {
                child.recycle()
            }
        }
        return null
    }

    /**
     * 辅助：从系统返回的 List 筛选第一个中心在屏幕内的节点，其余必须全部 recycle
     */
    private fun findFirstInBoundsAndRecycleRest(
        nodes: List<AccessibilityNodeInfo>?,
        screenBounds: Rect,
        extraFilter: ((AccessibilityNodeInfo) -> Boolean)? = null
    ): AccessibilityNodeInfo? {
        if (nodes.isNullOrEmpty() || screenBounds.isEmpty) return null

        var target: AccessibilityNodeInfo? = null
        for (node in nodes) {
            if (target == null && isCenterInBounds(node, screenBounds)) {
                if (extraFilter == null || extraFilter(node)) {
                    target = node
                    continue
                }
            }
            node.recycle()
        }
        return target
    }
}

// ==================== Kotlin DSL 扩展语法糖 ====================

/**
 * 针对 AccessibilityNodeInfo 的安全 DFS 查找扩展
 */
inline fun AccessibilityNodeInfo.findFirstVisible(
    bounds: Rect,
    crossinline predicate: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? {
    return AccessibilityNodeUtil.dfsFindNode(this, bounds) { predicate(it) }
}
