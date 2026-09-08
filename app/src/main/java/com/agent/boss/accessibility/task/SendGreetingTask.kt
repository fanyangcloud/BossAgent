package com.agent.boss.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.boss.accessibility.util.AccessibilityNodeUtil
import com.agent.boss.accessibility.util.GestureEngine

/**
 * 打招呼与个性化破冰发送闭环任务 (针对软键盘弹起与几何坐标校准版)
 */
class SendGreetingTask(
    private val greetingText: String,
    private val autoNavigateBack: Boolean = true
) : BaseAutomationTask() {

    private val tag = "SendGreetingTask"

    companion object {
        private const val STEP_INIT_AND_CLICK_CHAT = 1
        private const val STEP_WAIT_WINDOW_OR_POPUP = 2
        private const val STEP_INPUT_GREETING_TEXT = 3
        private const val STEP_CLICK_SEND_BUTTON = 4
        private const val STEP_VERIFY_AND_FINISH = 5
        private const val STEP_HANDLE_NAVIGATE_BACK = 6

        private const val ID_BTN_CHAT = "com.hpbr.bosszhipin:id/btn_chat"
        private const val ID_EDIT_TEXT = "com.hpbr.bosszhipin:id/editText_with_scrollbar"
        private const val ID_BACK_BUTTON = "com.hpbr.bosszhipin:id/iv_back"
    }

    override fun onStep(step: Int) {
        when (step) {
            STEP_INIT_AND_CLICK_CHAT -> handleInitAndClickChat()
            STEP_WAIT_WINDOW_OR_POPUP -> handleWaitWindowOrPopup()
            STEP_INPUT_GREETING_TEXT -> handleInputGreetingText()
            STEP_CLICK_SEND_BUTTON -> handleClickSendButton()
            STEP_VERIFY_AND_FINISH -> handleVerifyAndFinish()
            STEP_HANDLE_NAVIGATE_BACK -> handleNavigateBack()
        }
    }

    /**
     * 步骤 1：如果在详情页，点击“立即沟通”；如果已经处于聊天窗口，直接切入填词
     */
    private fun handleInitAndClickChat() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "根节点未就绪")
            return
        }

        // 场景 A：当前已经在聊天窗口内部
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        if (!editNodes.isNullOrEmpty()) {
            editNodes.forEach { it.recycle() }
            root.recycle()
            sendStep(STEP_INPUT_GREETING_TEXT, 150L)
            return
        }

        // 场景 B：在岗位详情页，点击底部的“立即沟通”按钮
        val chatBtnNodes = root.findAccessibilityNodeInfosByViewId(ID_BTN_CHAT)
        val chatBtn = chatBtnNodes?.firstOrNull()

        if (chatBtn != null) {
            sendTaskMessage("点击【立即沟通】按钮...")
            val clicked = GestureEngine.performClick(
                service = service,
                node = chatBtn,
                onSuccess = {
                    chatBtnNodes.forEach { it.recycle() }
                    root.recycle()
                    nextStep(850L) // 等待弹窗或聊天界面出现
                },
                onFail = {
                    chatBtnNodes.forEach { it.recycle() }
                    root.recycle()
                    retryCurrentStep(400L, "点击沟通按钮失败")
                }
            )
            if (!clicked) {
                chatBtnNodes.forEach { it.recycle() }
                root.recycle()
                retryCurrentStep(500L, "无法派发立即沟通点击手势")
            }
        } else {
            root.recycle()
            retryCurrentStep(500L, "未检测到聊天输入框或立即沟通按钮")
        }
    }

    /**
     * 步骤 2：判断是否出现快捷打招呼弹窗或直接进入聊天页
     */
    private fun handleWaitWindowOrPopup() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "等待界面渲染")
            return
        }

        // 1. 检查是否直接抵达了聊天页
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        if (!editNodes.isNullOrEmpty()) {
            editNodes.forEach { it.recycle() }
            root.recycle()
            sendStep(STEP_INPUT_GREETING_TEXT, 150L)
            return
        }

        // 2. 检查是否有底部弹窗按钮
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val confirmPopupBtn = AccessibilityNodeUtil.dfsFindNode(root, safeBounds) { node ->
            val txt = node.text?.toString() ?: ""
            txt == "发招呼" || txt == "确定" || txt == "确认"
        }

        if (confirmPopupBtn != null) {
            sendTaskMessage("检测到确认弹窗，点击推进...")
            GestureEngine.performClick(
                service = service,
                node = confirmPopupBtn,
                onSuccess = {
                    confirmPopupBtn.recycle()
                    root.recycle()
                    nextStep(850L)
                },
                onFail = {
                    confirmPopupBtn.recycle()
                    root.recycle()
                    retryCurrentStep(400L, "点击弹窗确认失败")
                }
            )
            return
        }

        root.recycle()
        retryCurrentStep(500L, "等待进入聊天窗口...")
    }

    /**
     * 步骤 3：定位输入框填入定制话术 (增加填词后等待时长，确保软键盘平稳升起)
     */
    private fun handleInputGreetingText() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "窗口未就绪")
            return
        }

        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()

        if (editNode == null) {
            root.recycle()
            retryCurrentStep(400L, "未找到输入框节点")
            return
        }

        sendTaskMessage("正在填入定制破冰语...")

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, greetingText)
        }
        val setTextSuccess = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        editNodes.forEach { it.recycle() }
        root.recycle()

        if (setTextSuccess) {
            Log.i(tag, "成功向输入框填入文本，预留 650ms 等待软键盘抬起稳定...")
            // 核心修复：留出足够时间（650ms）等待软键盘从 Y=2450 完全滑动到 Y=1606 并完成布局重排
            nextStep(650L)
        } else {
            retryCurrentStep(300L, "输入框填词失败")
        }
    }

    /**
     * 步骤 4：核心算法——根据键盘抬起后的最新物理坐标，毫米级精准点击发送按钮
     */
    private fun handleClickSendButton() {
        // 重新获取活跃窗口，拿到软键盘完全就绪后的最新布局树
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "无法获取发送界面根节点")
            return
        }

        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()

        if (editNode == null) {
            root.recycle()
            retryCurrentStep(400L, "定位发送按钮时丢失输入框锚点")
            return
        }

        val latestEditBounds = Rect()
        editNode.getBoundsInScreen(latestEditBounds)
        Log.d(tag, "软键盘抬起后输入框实时物理坐标: ${latestEditBounds.toShortString()}")

        // 优先查找是否有带有“发送”文本的独立节点
        val textNodes = root.findAccessibilityNodeInfosByText("发送")
        val sendTextCandidate = textNodes?.firstOrNull { it.isClickable || it.parent?.isClickable == true }

        if (sendTextCandidate != null) {
            Log.i(tag, "找到明确标注'发送'文本的节点，执行节点点击")
            GestureEngine.performClick(
                service = service,
                node = sendTextCandidate,
                onSuccess = {
                    textNodes.forEach { it.recycle() }
                    editNodes.forEach { it.recycle() }
                    root.recycle()
                    nextStep(700L)
                },
                onFail = {
                    textNodes.forEach { it.recycle() }
                    editNodes.forEach { it.recycle() }
                    root.recycle()
                    performCalibratedSendClick(latestEditBounds)
                }
            )
        } else {
            textNodes?.forEach { it.recycle() }
            editNodes.forEach { it.recycle() }
            root.recycle()
            // 采用基于屏幕几何的【毫米级发送按键标定算法】
            performCalibratedSendClick(latestEditBounds)
        }
    }

    /**
     * 核心标定：消除右侧间隙误差，正中发送按钮红心
     */
    private fun performCalibratedSendClick(editBounds: Rect) {
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val screenWidth = safeBounds.width()

        // 标定解析：
        // 屏幕右侧边距约为 48px，发送按钮宽度为 84px
        // 按钮物理中心 X = screenWidth - 48 - (84 / 2) = screenWidth - 90
        val targetX = screenWidth - 90
        // Y 轴直接对齐输入框当前的真实垂直中心（随软键盘上升自适应）
        val targetY = editBounds.centerY()

        sendTaskMessage("🎯 校准派发物理手势 -> 坐标: ($targetX, $targetY)")
        Log.i(tag, "执行精准发送点击: x=$targetX, y=$targetY")

        val clicked = GestureEngine.clickAt(
            service = service,
            x = targetX,
            y = targetY,
            onSuccess = {
                nextStep(700L)
            },
            onFail = {
                retryCurrentStep(400L, "物理坐标点击发送失败")
            }
        )

        if (!clicked) {
            retryCurrentStep(400L, "无法派发发送手势")
        }
    }

    /**
     * 步骤 5：校验消息是否已送达（输入框被清空则代表发送成功）
     */
    private fun handleVerifyAndFinish() {
        val root = service.rootInActiveWindow
        val editNodes = root?.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val currentText = editNodes?.firstOrNull()?.text?.toString() ?: ""

        editNodes?.forEach { it.recycle() }
        root?.recycle()

        // 如果输入框里的文字已经被清空，说明确实发出去了
        if (currentText.isEmpty()) {
            sendTaskMessage("✉️ 消息确认已送达！")
            Log.i(tag, "验证通过：输入框文字已清空，消息发送成功")
            nextStep(400L)
        } else {
            Log.w(tag, "输入框仍残留文字: '$currentText'，尝试补充触发一次物理回车...")
            // 兜底：再次点击一次标定坐标
            val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
            GestureEngine.clickAt(service, safeBounds.width() - 90, safeBounds.height() - 800) {
                nextStep(400L)
            }
        }
    }

    /**
     * 步骤 6：处理回退返回列表或详情
     */
    private fun handleNavigateBack() {
        if (!autoNavigateBack) {
            sendTaskMessage("🎉 打招呼闭环完成")
            finishTask()
            return
        }

        sendTaskMessage("沟通完毕，正在退回流界面...")
        val root = service.rootInActiveWindow
        val backNodes = root?.findAccessibilityNodeInfosByViewId(ID_BACK_BUTTON)
        val backBtn = backNodes?.firstOrNull()

        if (backBtn != null) {
            GestureEngine.performClick(
                service = service,
                node = backBtn,
                onSuccess = {
                    backNodes.forEach { it.recycle() }
                    root.recycle()
                    finishTask()
                },
                onFail = {
                    backNodes.forEach { it.recycle() }
                    root.recycle()
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    finishTask()
                }
            )
        } else {
            root?.recycle()
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            finishTask()
        }
    }
}