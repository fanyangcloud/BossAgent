# 🤖 BossAgent (Boss直聘自主求职智能体)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Powered By](https://img.shields.io/badge/LLM-DeepSeek-blueviolet.svg)](https://www.deepseek.com)

> 💡 **BossAgent** 是一个基于 Android 无障碍服务与 **DeepSeek 大语言模型** 驱动的自主求职助手。它模拟真实求职者的感知、思考与沟通链路，旨在从繁杂重复的海投中解放求职者。

---

## 🏛 核心架构设计

- **TaskDispatcher**：基于广播机制解耦调度，维护优先级任务队列与有限状态机。
- **四大协同 Agent**：
  - `SupervisorAgent`：负责验证码监控与异常熔断（最高权限）。
  - `ScoutAgent`：负责界面翻页巡视与 JD 数据清洗提取。
  - `EvaluatorAgent`：本地规则初筛 + DeepSeek 深度语义评分。
  - `CommunicatorAgent`：基于 JD 痛点和求职者优势动态生成定制开场白。
- **双模悬浮 HUD**：灵动胶囊防遮挡模式 + 极客面板（实时流式展示 DeepSeek 思考链）。
- **底层驱动**：贝塞尔曲线拟人轨迹滑动，防风控高斯抖动点击。
- **持久化层**：Room 数据库存储岗位、公司黑名单及沟通上下文；敏感 Key 加密存储。

---

## 📂 源码模块结构

```text
com.agent.boss/
├── accessibility/   # 底层驱动（无障碍服务、手势引擎、节点工具）
├── dispatcher/      # 调度中枢（广播协议合同、状态机、分发器）
├── agent/           # 智能体集群（Supervisor / Scout / Evaluator / Communicator）
├── llm/             # DeepSeek 引擎（API 客户端、Prompt 管理、JSON 结构体）
├── floating/        # 悬浮交互层（胶囊模式与控制台模式）
├── data/            # 存储层（Room DB、DAO、加密配置）
└── ui/              # 宿主配置 App（API Key 输入、简历编辑器）
