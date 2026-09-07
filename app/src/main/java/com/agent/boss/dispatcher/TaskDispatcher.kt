package com.agent.boss.dispatcher

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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
 * 任务调度中枢大脑 (已注入缓冲倒计时与冷启动防护)
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

    private val processedJobIdsInSession = mutableSetOf<String>()

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

    /**
     * 启动工作流：默认预留 warmUpDelayMs（如 4000ms）等待 App 启动
     */
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

        stateMachine.transitionTo(EngineState.SCANNING, "流水线启动")
        configRepository.setPaused(false)

        orchestratorJob = dispatcherScope.launch {
            // 1. 预热倒计时：给 Boss 直聘开屏和渲染留出充分时间
            if (warmUpDelayMs > 0) {
                val totalSeconds = (warmUpDelayMs / 1000).toInt()
                for (s in totalSeconds downTo 1) {
                    broadcastStepLog("🚀 正在拉起 Boss 直聘，将在 ${s}s 后开启寻岗...")
                    delay(1000L)
                }
            }

            // 2. 正式进入协作主循环
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

        stateMachine.transitionTo(EngineState.IDLE, "终止任务")
        processedJobIdsInSession.clear()
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
        stateMachine.transitionTo(EngineState.EMERGENCY_STOP, "安全风控熔断: $reason")
        stop()
    }

    private suspend fun runOrchestrationLoop() {
        while (dispatcherScope.isActive) {
            if (stateMachine.getCurrentState() == EngineState.PAUSED) {
                delay(1000L)
                continue
            }

            if (!stateMachine.getCurrentState().isOperating()) {
                break
            }

            val maxLimit = configRepository.getMaxDailyGreetings()
            if (!jobRepository.canGreetMoreToday(maxLimit)) {
                broadcastStepLog("今日打招呼已达最大上限 ($maxLimit 次)，任务完成！")
                stop()
                break
            }

            stateMachine.transitionTo(EngineState.SCANNING, "扫描当前屏幕卡片")
            
            // 温和等待列表界面就绪（最多等待 10 秒，每秒查一次，杜绝刚切换就报错）
            var cardAnchors = emptyList<ScoutAgent.CardAnchor>()
            var waitListSeconds = 0
            while (dispatcherScope.isActive && waitListSeconds < 10) {
                cardAnchors = scoutAgent.scanCurrentFeedCards()
                if (cardAnchors.isNotEmpty()) break
                broadcastStepLog("等待职位列表就绪 (${waitListSeconds + 1}s)...")
                delay(1000L)
                waitListSeconds++
            }

            if (cardAnchors.isEmpty()) {
                broadcastStepLog("未发现有效卡片，尝试向上滑动刷新一次...")
                val scrollDeferred = CompletableDeferred<Boolean>()
                scoutAgent.scrollNextPage(
                    allowTabSwitch = false,
                    onCompleted = { scrollDeferred.complete(true) },
                    onFailed = { scrollDeferred.complete(false) }
                )
                scrollDeferred.await()
                humanDelay(2000L, 300L)
                continue
            }

            for (card in cardAnchors) {
                if (!dispatcherScope.isActive || !stateMachine.getCurrentState().isOperating()) break

                while (stateMachine.getCurrentState() == EngineState.PAUSED) {
                    delay(1000L)
                }

                stateMachine.transitionTo(EngineState.INSPECTING, "查看岗位详情")
                val scrapeDeferred = CompletableDeferred<ScrapedRawJob?>()

                scoutAgent.inspectJobDetail(
                    cardBounds = card.bounds,
                    onSuccess = { scrapedJob -> scrapeDeferred.complete(scrapedJob) },
                    onFailed = { scrapeDeferred.complete(null) }
                )

                val job = scrapeDeferred.await()
                if (job == null) {
                    continue
                }

                val jobId = job.resolveJobId()
                if (processedJobIdsInSession.contains(jobId)) {
                    continue
                }
                processedJobIdsInSession.add(jobId)

                stateMachine.transitionTo(EngineState.THINKING, "DeepSeek 评估中")
                val evalResult = evaluatorAgent.evaluate(job)

                humanDelay(1200L, 300L)

                if (evalResult.isApproved) {
                    stateMachine.transitionTo(EngineState.COMMUNICATING, "发起打招呼破冰")
                    val greetDeferred = CompletableDeferred<Boolean>()

                    communicatorAgent.executeGreeting(
                        rawJob = job,
                        evaluatorResult = evalResult,
                        onSuccess = { greetDeferred.complete(true) },
                        onFailed = { greetDeferred.complete(false) }
                    )

                    greetDeferred.await()
                    humanDelay(2500L, 500L)
                }

                stateMachine.transitionTo(EngineState.SCANNING, "准备探寻下一个卡片")
            }

            if (dispatcherScope.isActive && stateMachine.getCurrentState().isOperating()) {
                val scrollDeferred = CompletableDeferred<Boolean>()
                scoutAgent.scrollNextPage(
                    allowTabSwitch = true,
                    onCompleted = { scrollDeferred.complete(true) },
                    onFailed = { scrollDeferred.complete(false) }
                )
                scrollDeferred.await()
                humanDelay(1500L, 300L)
            }
        }
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
