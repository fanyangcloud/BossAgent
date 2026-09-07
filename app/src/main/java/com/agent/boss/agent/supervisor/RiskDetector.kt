package com.agent.boss.agent.supervisor

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 平台风控特征侦测引擎
 */
object RiskDetector {

    enum class RiskType(val description: String) {
        CAPTCHA_SLIDER("滑动拼图验证码"),
        FREQUENT_OPERATION("操作过于频繁限制"),
        FACE_RECOGNITION("人脸实名核验"),
        ACCOUNT_RESTRICTED("账号状态异常阻断"),
        IP_BLOCKED("网络异常/IP环境风险")
    }

    data class DetectionResult(
        val type: RiskType,
        val matchedKeyword: String,
        val isCritical: Boolean = true // 是否必须立即强制拉闸
    )

    // 各类风控特征关键词库
    private val sliderKeywords = listOf(
        "拖动下方滑块", "拖动滑块完成拼图", "请完成安全验证",
        "按住滑块", "拖动滑块", "安全校验", "滑动验证"
    )

    private val frequentKeywords = listOf(
        "操作过于频繁", "请稍后再试", "访问过于频繁",
        "你的操作太快了", "歇一歇再试", "系统繁忙"
    )

    private val faceKeywords = listOf(
        "人脸识别", "扫脸认证", "实名核验", "请进行人脸验证"
    )

    private val accountKeywords = listOf(
        "账号已被限制", "账号存在异常", "暂停打招呼功能",
        "已被封禁", "已达今日沟通上限"
    )

    /**
     * 基于文本流侦测风控
     */
    fun detectFromText(text: String): DetectionResult? {
        if (text.isBlank()) return null

        for (kw in sliderKeywords) {
            if (text.contains(kw)) return DetectionResult(RiskType.CAPTCHA_SLIDER, kw)
        }
        for (kw in faceKeywords) {
            if (text.contains(kw)) return DetectionResult(RiskType.FACE_RECOGNITION, kw)
        }
        for (kw in frequentKeywords) {
            if (text.contains(kw)) return DetectionResult(RiskType.FREQUENT_OPERATION, kw)
        }
        for (kw in accountKeywords) {
            if (text.contains(kw)) return DetectionResult(RiskType.ACCOUNT_RESTRICTED, kw)
        }

        return null
    }

    /**
     * 基于页面无障碍节点树全量扫描风控元素
     */
    fun detectFromNode(root: AccessibilityNodeInfo?): DetectionResult? {
        if (root == null) return null

        val nodeText = buildNodeTextRecursive(root)
        return detectFromText(nodeText)
    }

    private fun buildNodeTextRecursive(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()

        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                sb.append(buildNodeTextRecursive(child))
                child.recycle()
            }
        }
        return sb.toString()
    }
}
