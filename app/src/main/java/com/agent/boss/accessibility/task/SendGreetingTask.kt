package com.agent.boss.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.boss.accessibility.util.AccessibilityNodeUtil
import com.agent.boss.accessibility.util.GestureEngine

/**
 * 打招呼与个性化破冰发送闭环任务
 *
 * 核心流程：
 * 1. 在详情页点击“立即沟通” (btn_chat)
 * 2. 智能等待：处理可能弹出的快捷招呼弹窗 或 直接进入的聊天窗口
 * 3. 定位输入框 (editText_with_scrollbar) 并填入定制打招呼文本
 * 4. 三级级联算法精准定位无 ID 的发送按钮并执行物理坐标点击
 * 5. (可选) 点击左上角返回或全局返回退回原界面
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
        private const val STEP_HANDLE_NAVIGATE_BACK = 5

        // XML 抓取到的真实 Resource ID
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

        // 场景 A：如果当前已经在聊天窗口内部了
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
                    nextStep(800L) // 等待弹窗或聊天界面出现
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
            retryCurrentStep(500L, "既未检测到聊天输入框，也未检测到立即沟通按钮")
        }
    }

    /**
     * 步骤 2：判断是否出现确认弹窗（如“确定沟通”/“发送”）或直接进入聊天页
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

        // 2. 检查是否有底部弹窗按钮 (部分账号初次沟通会弹出“打招呼”或“确定”确认框)
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
                    nextStep(800L)
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
     * 步骤 3：定位输入框并设置个性化打招呼话术
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

        // 使用系统级原生 ACTION_SET_TEXT 注入文本（快速、稳定、不污染系统剪贴板）
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, greetingText)
        }
        val setTextSuccess = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        editNodes.forEach { it.recycle() }
        root.recycle()

        if (setTextSuccess) {
            Log.i(tag, "成功向输入框填入文本: $greetingText")
            // 停顿 350ms，模拟输入完成后的思考间隙，让界面的“发送”状态变亮
            nextStep(350L)
        } else {
            retryCurrentStep(300L, "输入框填词失败")
        }
    }

    /**
     * 步骤 4：核心算法——精准定位无 ID 发送按钮并点击
     */
    private fun handleClickSendButton() {
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

        val editBounds = Rect()
        editNode.getBoundsInScreen(editBounds)

        // 尝试定位无 ID 发送按钮
        val sendBtnNode = findSendButtonRobust(root, editNode, editBounds)

        if (sendBtnNode != null) {
            val sendRect = Rect()
            sendBtnNode.getBoundsInScreen(sendRect)
            sendTaskMessage("🎯 精准锚定发送按钮: ${sendRect.toShortString()}，派发物理手势")

            GestureEngine.performClick(
                service = service,
                node = sendBtnNode,
                onSuccess = {
                    sendBtnNode.recycle()
                    editNodes.forEach { it.recycle() }
                    root.recycle()
                    nextStep(800L) // 等待消息发出去
                },
                onFail = {
                    sendBtnNode.recycle()
                    editNodes.forEach { it.recycle() }
                    root.recycle()
                    fallbackCoordinateClick(editBounds)
                }
            )
        } else {
            editNodes.forEach { it.recycle() }
            root.recycle()
            // 节点检索不到时的绝对兜底：屏幕右侧物理坐标盲击
            fallbackCoordinateClick(editBounds)
        }
    }

    /**
     * 三级级联发送按钮检索算法
     */
    private fun findSendButtonRobust(
        root: AccessibilityNodeInfo,
        editNode: AccessibilityNodeInfo,
        editBounds: Rect
    ): AccessibilityNodeInfo? {
        // 策略 1：检查是否有节点直接带有“发送”文本（部分版本填词后由图标变为“发送”两字）
        val textNodes = root.findAccessibilityNodeInfosByText("发送")
        if (!textNodes.isNullOrEmpty()) {
            val candidate = textNodes.firstOrNull { it.isClickable || it.parent?.isClickable == true }
            textNodes.filter { it != candidate }.forEach { it.recycle() }
            if (candidate != null) return candidate
        }

        // 策略 2：基于输入框父容器 (LinearLayout) 取最后一个子视图（对应 XML 中 index="3" 的 ImageView）
        val parent = editNode.parent
        if (parent != null) {
            val childCount = parent.childCount
            if (childCount > 1) {
                val lastChild = parent.getChild(childCount - 1)
                if (lastChild != null) {
                    val lastRect = Rect()
                    lastChild.getBoundsInScreen(lastRect)
                    // 确认该子视图位于输入框右侧且在同一水平带
                    if (lastRect.left >= editBounds.right && Math.abs(lastRect.centerY() - editBounds.centerY()) < 80) {
                        parent.recycle()
                        return lastChild
                    }
                    lastChild.recycle()
                }
            }
            parent.recycle()
        }

        // 策略 3：DFS 查找处于输入框右侧且位于屏幕右侧 85% 以外的 ImageView
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val minX = (safeBounds.width() * 0.85f).toInt()

        return AccessibilityNodeUtil.dfsFindNode(root, safeBounds) { node ->
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            nodeRect.left >= minX &&
            Math.abs(nodeRect.centerY() - editBounds.centerY()) < 80 &&
            (node.isClickable || node.className?.contains("ImageView") == true)
        }
    }

    /**
     * 终极兜底：基于输入框水平线向右侧偏移的物理坐标盲击
     */
    private fun fallbackCoordinateClick(editBounds: Rect) {
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        // 取输入框右边缘与屏幕右边缘中间位置
        val targetX = (editBounds.right + safeBounds.width()) / 2
        val targetY = editBounds.centerY()

        sendTaskMessage("⚠️ 触发物理坐标兜底盲击: ($targetX, $targetY)")
        GestureEngine.clickAt(
            service = service,
            x = targetX,
            y = targetY,
            onSuccess = { nextStep(800L) },
            onFail = { retryCurrentStep(400L, "物理坐标盲击失败") }
        )
    }

    /**
     * 步骤 5：处理回退返回列表或详情
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
