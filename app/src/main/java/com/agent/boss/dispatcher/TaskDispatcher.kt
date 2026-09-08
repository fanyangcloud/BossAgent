package com.agent.boss.dispatcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.agent.boss.accessibility.BossAccessibilityService
import com.agent.boss.accessibility.model.PageScene
import com.agent.boss.accessibility.model.ScrapedRawJob
import com.agent.boss.agent.communicator.CommunicatorAgent
import com.agent.boss.agent.evaluator.EvaluatorAgent
import com.agent.boss.agent.scout.ScoutAgent
import com.agent.boss.agent.supervisor.SupervisorAgent
import com.agent.boss.data.repository.ConfigRepository
import com.agent.boss.data.repository.JobRepository
import com.agent.boss.dispatcher.contract.DispatcherBroadcasts
import com.agent.boss.dispatcher.fsm.EngineState
import com.agent.boss.dispatcher.fsm.StateMachine
import com.agent.boss.dispatcher.receiver.AccessibilityFeedbackReceiver
import com.agent.boss.dispatcher.receiver.SupervisorControlReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ThreadLocalRandom

/**
 * 任务调度中枢大脑 (强化自愈守护、前置去重与决策闭环版)
 */
class TaskDispatcher(
    private val context: Context,
    private val stateMachine: StateMachine,
    private val supervisorAgent: SupervisorAgent,
    private val scoutAgent: ScoutAgent,
    private val evaluatorAgent: EvaluatorAgent,
    private val communicatorAgent: CommunicatorAgent,
    private val configRepository: ConfigRepository,
    private val jobRepository: JobRepository
) {

    private val tag = "TaskDispatcher"
    private var dispatcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var orchestratorJob: Job? = null

    private val feedbackReceiver = AccessibilityFeedbackReceiver()
    private val controlReceiver = SupervisorControlReceiver()

    // 会话内存去重集合 (记录 "公司名_职位名")
    private val processedJobKeys = mutableSetOf<String>()

    // 当前感知的视觉场景
    @Volatile
    private var currentScene: PageScene = PageScene.UNKNOWN

    init {
        setupStateListener()
        setupReceivers()
    }

    private fun setupStateListener() {
        stateMachine.addListener { oldState, newState, reason ->
            Log.i(tag, "引擎状态跃迁: $oldState -> $newState ($reason)")
            val intent = Intent(DispatcherBroadcasts.ACTION_ENGINE_STATE_CHANGED).apply {
                putExtra(DispatcherBroadcasts.EXTRA_OLD_STATE, oldState.name)
                putExtra(DispatcherBroadcasts.EXTRA_NEW_STATE, newState.name)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    private fun setupReceivers() {
        feedbackReceiver.onSceneChanged = { scene ->
            Log.d(tag, "感知到视觉场景变化: $scene")
            currentScene = scene
        }

        controlReceiver.onStartCommand = { start() }
        controlReceiver.onStopCommand = { stop() }
        controlReceiver.onPauseCommand = { pause() }
        controlReceiver.onResumeCommand = { resume() }
        controlReceiver.onEmergencyStopCommand = { riskType, reason ->
            emergencyStop(riskType, reason)
        }

        val exportFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else 0

        context.registerReceiver(feedbackReceiver, AccessibilityFeedbackReceiver.createIntentFilter(), exportFlag)
        context.registerReceiver(controlReceiver, SupervisorControlReceiver.createIntentFilter(), exportFlag)
    }

    fun start(warmUpDelayMs: Long = 4000L) {
        if (stateMachine.getCurrentState().isOperating()) {
            Log.w(tag, "调度器已经在运行中")
            return
        }

        Log.i(tag, "🚀 启动 TaskDispatcher 工作流...")
        dispatcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        supervisorAgent.start()
        scoutAgent.start()
        evaluatorAgent.start()
        communicatorAgent.start()

        safeTransitionTo(EngineState.SCANNING, "流水线启动")
        configRepository.setPaused(false)

        orchestratorJob = dispatcherScope.launch {
            if (warmUpDelayMs > 0) {
                val totalSeconds = (warmUpDelayMs / 1000).toInt()
                for (s in totalSeconds downTo 1) {
                    broadcastStepLog("🚀 正在拉起 Boss 直聘，将在 ${s}s 后开启寻岗...")
                    delay(1000L)
                }
            }

            runOrchestrationLoop()
        }
    }

    fun stop() {
        Log.w(tag, "🛑 停止 TaskDispatcher 工作流")
        orchestratorJob?.cancel()
        orchestratorJob = null

        supervisorAgent.stop()
        scoutAgent.stop()
        evaluatorAgent.stop()
        communicatorAgent.stop()

        safeTransitionTo(EngineState.IDLE, "终止任务")
        processedJobKeys.clear()
    }

    fun pause() {
        if (stateMachine.transitionTo(EngineState.PAUSED, "暂停指令")) {
            configRepository.setPaused(true)
            broadcastStepLog("⏸️ 流水线已挂起")
        }
    }

    fun resume() {
        if (stateMachine.transitionTo(EngineState.SCANNING, "恢复执行")) {
            configRepository.setPaused(false)
            broadcastStepLog("▶️ 流水线已恢复")
        }
    }

    fun emergencyStop(riskType: String, reason: String) {
        safeTransitionTo(EngineState.EMERGENCY_STOP, "安全风控熔断: $reason")
        stop()
    }

    /**
     * 核心协调主循环
     */
    private suspend fun runOrchestrationLoop() {
        while (dispatcherScope.isActive) {
            if (stateMachine.getCurrentState() == EngineState.PAUSED) {
                delay(1000L)
                continue
            }

            if (!stateMachine.getCurrentState().isOperating()) {
                break
            }

            // 1. 每日配额校验
            val maxLimit = configRepository.getMaxDailyGreetings()
            if (!jobRepository.canGreetMoreToday(maxLimit)) {
                broadcastStepLog("今日打招呼已达最大上限 ($maxLimit 次)，任务圆满完成！")
                stop()
                break
            }

            // 2. 核心守护：扫描前必须确保停留在【推荐职位列表】
            ensureInRecommendList()
            safeTransitionTo(EngineState.SCANNING, "扫描当前屏幕卡片")

            // 3. 扫描屏幕可见卡片 (带就绪等待)
            var cardAnchors = emptyList<ScoutAgent.CardAnchor>()
            var waitListSeconds = 0
            while (dispatcherScope.isActive && waitListSeconds < 8) {
                cardAnchors = scoutAgent.scanCurrentFeedCards()
                if (cardAnchors.isNotEmpty()) break
                broadcastStepLog("等待职位列表卡片呈现 (${waitListSeconds + 1}s)...")
                delay(1000L)
                waitListSeconds++
            }

            // 4. 若当前屏幕连一张卡片都没有，上滑翻页
            if (cardAnchors.isEmpty()) {
                broadcastStepLog("未发现有效卡片，上滑加载新内容...")
                performScrollFeed(allowTabSwitch = false)
                humanDelay(1500L, 300L)
                continue
            }

            // 5. 【前置秒级去重】：在点击前直接滤掉已看过的岗位
            val unvisitedCards = cardAnchors.filter { card ->
                val key = buildJobKey(card.previewCompany, card.previewTitle)
                !processedJobKeys.contains(key)
            }

            if (unvisitedCards.isEmpty()) {
                broadcastStepLog("当前屏幕卡片均已检视，上滑浏览下一页...")
                performScrollFeed(allowTabSwitch = true)
                humanDelay(1500L, 300L)
                continue
            }

            // 6. 逐个处理未访问的卡片
            for (card in unvisitedCards) {
                if (!dispatcherScope.isActive || !stateMachine.getCurrentState().isOperating()) break

                while (stateMachine.getCurrentState() == EngineState.PAUSED) {
                    delay(1000L)
                }

                val cardKey = buildJobKey(card.previewCompany, card.previewTitle)
                processedJobKeys.add(cardKey) // 立即锁定，避免同一会话二次点击

                // 点击进入详情前，再次确认处于推荐列表
                ensureInRecommendList()

                // 阶段一：进入详情页抓取 JD (此时 InspectDetailTask 只进不退)
                safeTransitionTo(EngineState.INSPECTING, "查看岗位详情")
                val scrapeDeferred = CompletableDeferred<ScrapedRawJob?>()

                scoutAgent.inspectJobDetail(
                    cardBounds = card.bounds,
                    onSuccess = { scrapedJob -> scrapeDeferred.complete(scrapedJob) },
                    onFailed = { scrapeDeferred.complete(null) }
                )

                val job = scrapeDeferred.await()
                if (job == null) {
                    broadcastStepLog("⚠️ 提取岗位详情失败，安全回退")
                    ensureInRecommendList()
                    continue
                }

                // 记录完整 ID
                processedJobKeys.add(job.resolveJobId())

                // 阶段二：DeepSeek 大脑认知评估
                safeTransitionTo(EngineState.THINKING, "DeepSeek 评估中")
                broadcastStepLog("🧠 DeepSeek 正在评估【${job.companyName} - ${job.title}】...")
                val evalResult = evaluatorAgent.evaluate(job)

                humanDelay(1000L, 200L)

                // 阶段三：依据决策闭环分流 (使用准确的 evalResult.matchScore)
                if (evalResult.isApproved) {
                    // 分支 A：契合度通过！在当前的详情页直接发起打招呼
                    safeTransitionTo(EngineState.COMMUNICATING, "发起打招呼破冰")
                    broadcastStepLog("🎯 契合度达标 (${evalResult.matchScore}分)，立即发起破冰沟通...")

                    val greetDeferred = CompletableDeferred<Boolean>()
                    communicatorAgent.executeGreeting(
                        rawJob = job,
                        evaluatorResult = evalResult,
                        onSuccess = { greetDeferred.complete(true) },
                        onFailed = { greetDeferred.complete(false) }
                    )

                    greetDeferred.await()
                    humanDelay(2000L, 400L)
                } else {
                    // 分支 B：契合度不足！
                    broadcastStepLog("⏭️ 契合度不足 (${evalResult.matchScore}分)，跳过该岗位")
                }

                // 阶段四：无论是沟通完成还是跳过，一律安全退回【推荐职位列表】！
                ensureInRecommendList()
                humanDelay(800L, 200L)
            }

            // 7. 当前屏所有新卡片消费完毕，上滑翻页
            if (dispatcherScope.isActive && stateMachine.getCurrentState().isOperating()) {
                broadcastStepLog("当前一屏已巡查完毕，上滑刷新职位流...")
                performScrollFeed(allowTabSwitch = true)
                humanDelay(1500L, 300L)
            }
        }
    }

    /**
     * 自愈场景守卫：强制确保当前视口回退并稳定在【推荐职位列表】
     */
    private suspend fun ensureInRecommendList(maxAttempts: Int = 4) {
        val service = BossAccessibilityService.instance ?: return
        var attempts = 0

        while (dispatcherScope.isActive && attempts < maxAttempts) {
            // 如果场景已经是推荐列表，自愈完成
            if (currentScene == PageScene.RECOMMEND_LIST) {
                return
            }

            // 如果处于 详情页 或 聊天页，调用全局后退动作
            Log.w(tag, "检测到未在推荐列表 (当前: $currentScene)，执行安全后退自愈 (第 ${attempts + 1} 次)...")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            humanDelay(800L, 150L)
            attempts++
        }
    }

    private suspend fun performScrollFeed(allowTabSwitch: Boolean) {
        val scrollDeferred = CompletableDeferred<Boolean>()
        scoutAgent.scrollNextPage(
            allowTabSwitch = allowTabSwitch,
            onCompleted = { scrollDeferred.complete(true) },
            onFailed = { scrollDeferred.complete(false) }
        )
        scrollDeferred.await()
    }

    private fun safeTransitionTo(targetState: EngineState, reason: String) {
        if (stateMachine.getCurrentState() != targetState) {
            stateMachine.transitionTo(targetState, reason)
        }
    }

    private fun buildJobKey(company: String, title: String): String {
        return "${company.trim()}_${title.trim()}"
    }

    private fun broadcastStepLog(message: String) {
        val intent = Intent(DispatcherBroadcasts.ACTION_TASK_STEP_MESSAGE).apply {
            putExtra(DispatcherBroadcasts.EXTRA_TASK_NAME, "TaskDispatcher")
            putExtra(DispatcherBroadcasts.EXTRA_STEP_INFO, message)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    private suspend fun humanDelay(baseMs: Long, varianceMs: Long = 300L) {
        val speedFactor = configRepository.getSpeedFactor()
        val adjustedBase = (baseMs * speedFactor).toLong()
        val jitter = ThreadLocalRandom.current().nextLong(-varianceMs, varianceMs)
        val finalDelay = maxOf(300L, adjustedBase + jitter)
        delay(finalDelay)
    }

    fun destroy() {
        stop()
        try {
            context.unregisterReceiver(feedbackReceiver)
            context.unregisterReceiver(controlReceiver)
        } catch (e: Exception) {
            Log.e(tag, "注销中枢广播异常: ${e.message}")
        }
        dispatcherScope.cancel()
    }
}