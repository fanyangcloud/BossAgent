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
 * 任务调度中枢大脑 (TaskDispatcher)
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

    // 记录本轮巡查已经处理过的 JobId，防止重复点击同一卡片
    private val processedJobIdsInSession = mutableSetOf<String>()

    init {
        setupStateListener()
        setupReceivers()
    }

    // ==================== 状态机与广播绑定 ====================

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

    // ==================== 调度生命周期控制 ====================

    fun start() {
        if (stateMachine.getCurrentState().isOperating()) {
            Log.w(tag, "调度器已经在运行中")
            return
        }

        Log.i(tag, "🚀 启动 TaskDispatcher 全自主工作流...")
        dispatcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        supervisorAgent.start()
        scoutAgent.start()
        evaluatorAgent.start()
        communicatorAgent.start()

        stateMachine.transitionTo(EngineState.SCANNING, "流水线启动")
        configRepository.setPaused(false)

        orchestratorJob = dispatcherScope.launch {
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

        stateMachine.transitionTo(EngineState.IDLE, "用户手动终止")
        processedJobIdsInSession.clear()
    }

    fun pause() {
        if (stateMachine.transitionTo(EngineState.PAUSED, "暂停指令")) {
            configRepository.setPaused(true)
            Log.i(tag, "⏸️ 调度流水线已挂起")
        }
    }

    fun resume() {
        if (stateMachine.transitionTo(EngineState.SCANNING, "恢复执行")) {
            configRepository.setPaused(false)
            Log.i(tag, "▶️ 调度流水线恢复运行")
        }
    }

    fun emergencyStop(riskType: String, reason: String) {
        stateMachine.transitionTo(EngineState.EMERGENCY_STOP, "安全风控熔断: $reason")
        stop()
    }

    // ==================== 核心自主寻岗协作主循环 ====================

    private suspend fun runOrchestrationLoop() {
        while (dispatcherScope.isActive) {
            // 1. 暂停态自旋等待
            if (stateMachine.getCurrentState() == EngineState.PAUSED) {
                delay(1000L)
                continue
            }

            if (!stateMachine.getCurrentState().isOperating()) {
                break
            }

            // 2. 检查单日投递限额
            val maxLimit = configRepository.getMaxDailyGreetings()
            if (!jobRepository.canGreetMoreToday(maxLimit)) {
                Log.i(tag, "今日已达最大打招呼额度 ($maxLimit)，任务平稳收尾")
                stop()
                break
            }

            // 3. 进入扫描态：抓取当前屏的有效卡片
            stateMachine.transitionTo(EngineState.SCANNING, "扫描当前屏幕卡片")
            val cardAnchors = scoutAgent.scanCurrentFeedCards()

            var processedCardInScreen = 0

            for (card in cardAnchors) {
                if (!dispatcherScope.isActive || !stateMachine.getCurrentState().isOperating()) break

                // 4. 检查是否正在暂停
                while (stateMachine.getCurrentState() == EngineState.PAUSED) {
                    delay(1000L)
                }

                // 5. 详查态：深入详情提取全量 JD
                stateMachine.transitionTo(EngineState.INSPECTING, "查看岗位详情")
                val scrapeDeferred = CompletableDeferred<ScrapedRawJob?>()

                scoutAgent.inspectJobDetail(
                    cardBounds = card.bounds,
                    onSuccess = { scrapedJob -> scrapeDeferred.complete(scrapedJob) },
                    onFailed = { scrapeDeferred.complete(null) }
                )

                val job = scrapeDeferred.await()
                if (job == null) {
                    Log.w(tag, "提取详情失败，跳过该卡片")
                    continue
                }

                val jobId = job.resolveJobId()
                if (processedJobIdsInSession.contains(jobId)) {
                    continue
                }
                processedJobIdsInSession.add(jobId)
                processedCardInScreen++

                // 6. 思考态：交给 Evaluator 参谋进行双引擎评估
                stateMachine.transitionTo(EngineState.THINKING, "DeepSeek 评估中")
                val evalResult = evaluatorAgent.evaluate(job)

                // 拟人化思考停顿 (基于高斯分布)
                humanDelay(1200L, 300L)

                // 7. 沟通态：若通过评估，由 Communicator 破冰沟通
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
                    // 成功打招呼后的防封冷静期 (2000ms ~ 3500ms)
                    humanDelay(2500L, 500L)
                }

                stateMachine.transitionTo(EngineState.SCANNING, "准备探寻下一个卡片")
            }

            // 8. 当前屏幕扫描完毕，拟人化翻页
            if (dispatcherScope.isActive && stateMachine.getCurrentState().isOperating()) {
                val scrollDeferred = CompletableDeferred<Boolean>()
                scoutAgent.scrollNextPage(
                    allowTabSwitch = true,
                    onCompleted = { scrollDeferred.complete(true) },
                    onFailed = { scrollDeferred.complete(false) }
                )
                scrollDeferred.await()
                // 翻页后列表稳定缓冲停顿
                humanDelay(1500L, 300L)
            }
        }
    }

    /**
     * 拟人化高斯延迟函数 (模拟人类反应时间随机抖动，抗击机械行为分析)
     */
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
