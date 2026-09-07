package com.agent.boss

import android.app.Application
import android.util.Log
import com.agent.boss.agent.communicator.CommunicatorAgent
import com.agent.boss.agent.evaluator.EvaluatorAgent
import com.agent.boss.agent.scout.ScoutAgent
import com.agent.boss.agent.supervisor.SupervisorAgent
import com.agent.boss.data.local.AppDatabase
import com.agent.boss.data.pref.AppSettings
import com.agent.boss.data.pref.EncryptedDataStore
import com.agent.boss.data.repository.ConfigRepository
import com.agent.boss.data.repository.JobRepository
import com.agent.boss.dispatcher.TaskDispatcher
import com.agent.boss.dispatcher.fsm.StateMachine
import com.agent.boss.llm.DeepSeekClient
import com.agent.boss.llm.filter.LocalPreFilter
import com.agent.boss.llm.limiter.TokenUsageTracker
import com.agent.boss.llm.prompt.PromptManager

/**
 * 全局 Application：统一依赖图谱装配
 */
class BossApp : Application() {

    private val tag = "BossApp"

    // 全局持久化与仓储单例
    lateinit var database: AppDatabase private set
    lateinit var encryptedDataStore: EncryptedDataStore private set
    lateinit var appSettings: AppSettings private set
    lateinit var configRepository: ConfigRepository private set
    lateinit var jobRepository: JobRepository private set

    // 大模型认知层单例
    lateinit var deepSeekClient: DeepSeekClient private set
    lateinit var tokenUsageTracker: TokenUsageTracker private set
    lateinit var promptManager: PromptManager private set
    lateinit var localPreFilter: LocalPreFilter private set

    // 多智能体集群单例
    lateinit var stateMachine: StateMachine private set
    lateinit var supervisorAgent: SupervisorAgent private set
    lateinit var scoutAgent: ScoutAgent private set
    lateinit var evaluatorAgent: EvaluatorAgent private set
    lateinit var communicatorAgent: CommunicatorAgent private set

    // 调度总指挥中枢
    lateinit var taskDispatcher: TaskDispatcher private set

    companion object {
        lateinit var instance: BossApp private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(tag, "BossApp 进程启动，开始全链路依赖装配...")

        initStorageLayer()
        initCognitiveLayer()
        initAgentCluster()
        initDispatcher()

        Log.i(tag, "✅ BossAgent 全系统依赖注入装配完成！")
    }

    private fun initStorageLayer() {
        database = AppDatabase.getInstance(this)
        encryptedDataStore = EncryptedDataStore.getInstance(this)
        appSettings = AppSettings.getInstance(this)
        configRepository = ConfigRepository(encryptedDataStore, appSettings)
        jobRepository = JobRepository(database)
    }

    private fun initCognitiveLayer() {
        tokenUsageTracker = TokenUsageTracker(jobRepository)
        promptManager = PromptManager(configRepository)
        localPreFilter = LocalPreFilter(jobRepository, configRepository)
        deepSeekClient = DeepSeekClient.getInstance(
            configRepository,
            jobRepository,
            tokenUsageTracker,
            promptManager
        )
    }

    private fun initAgentCluster() {
        stateMachine = StateMachine()
        supervisorAgent = SupervisorAgent(this)
        scoutAgent = ScoutAgent(this)
        evaluatorAgent = EvaluatorAgent(
            this,
            localPreFilter,
            deepSeekClient,
            jobRepository,
            configRepository
        )
        communicatorAgent = CommunicatorAgent(
            this,
            deepSeekClient,
            jobRepository,
            configRepository
        )
    }

    private fun initDispatcher() {
        taskDispatcher = TaskDispatcher(
            context = this,
            stateMachine = stateMachine,
            supervisorAgent = supervisorAgent,
            scoutAgent = scoutAgent,
            evaluatorAgent = evaluatorAgent,
            communicatorAgent = communicatorAgent,
            configRepository = configRepository,
            jobRepository = jobRepository
        )
    }
}
