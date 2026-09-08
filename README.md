<div align="center">

# 🦌 鹿鹿 (Lulu) · 端侧智能求职 Agent
### 首款专为现代求职者打造的 Android 原生 Multi-Agent 治愈系求职搭子

<p align="center">
  <img src="https://img.shields.io/badge/Language-Kotlin_100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Platform-Android_Native-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/AI_Engine-DeepSeek_R1%2FV3-4D6BFE?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Architecture-Multi--Agent_%2B_FSM-FF6B6B?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue?style=for-the-badge" />
</p>

<p align="center">
  <strong>“懂技术的极速推土机，更懂 I 人的温柔治愈系求职军师。”</strong>
</p>

<p align="center">
  <a href="#-为什么选择鹿鹿">为什么不同</a> •
  <a href="#-核心分流模式e人-vs-i人">E/I人专属模式</a> •
  <a href="#-系统架构设计">架构设计</a> •
  <a href="#-极速安装使用">极速上手</a> •
  <a href="#-常见问题与免责声明">免责声明</a>
</p>

---

</div>

## 💡 为什么选择 鹿鹿 (Lulu)？

市面上 99% 的自动求职工具都是粗糙的浏览器脚本（Chrome 插件/Playwright），面临着 **Web 端风控频繁封号、Cookie 动辄失效、配置繁琐、无法感知移动端 HR 实时状态** 的致命硬伤。

**鹿鹿 (Lulu)** 彻底颠覆了这一切：
- 📱 **真机 Android 原生驱动**：运行在真实手机端，三阶贝塞尔拟人轨迹手势 + 高斯抖动，告别封号风险。
- 🧠 **端侧多智能体集群（Multi-Agent）**：Scout（侦察）、Evaluator（评估）、Supervisor（风控）、Communicator（谈判）四位一体。
- 🌸 **专为社恐/I 人设计的情绪缓冲**：不只是冰冷的投递工具，更是为你挡在前面、安抚焦虑、提供高情商话术的治愈系求职搭子。
- 💰 **极致的 Token 成本控制**：内置本地规则过滤器（0 Token 消耗）+ JD 指纹去重缓存，拒绝盲目消耗 API。
- 🛡️ **100% 隐私与纯端侧安全**：无中心化数据服务器，简历与 API Key 均存储在手机本地加密容器（EncryptedDataStore）。

---

## 🎭 核心分流模式：懂 E 人的效率，更懂 I 人的内耗

<div align="center">

```text
               ┌───────────────────────────────────────────────┐
               │         启动 鹿鹿：选择你的专属求职人格       │
               └───────────────────────┬───────────────────────┘
                                       │
            ┌──────────────────────────┴──────────────────────────┐
            ▼                                                     ▼
   🔥 【E人·极速推土机流】                                🌸 【I人·治愈系护航流】
   • 核心：效率拉满，并发海聊海投                        • 核心：安全感、拒绝尴尬、心理缓冲
   • 策略：后台全自动拟人扫街                            • 策略：先看鹿鹿精选日报 ➔ 确认后再投
   • 鹿鹿状态：元气执行官（“冲冲冲！”）                  • 鹿鹿状态：贴心闺蜜（“别怕，有我在~”）
                                                                  │
                                                       ┌──────────┴──────────┐
                                                       ▼                     ▼
                                             💡 [鹿鹿当参谋 (半自动)]    🤖 [鹿鹿全托管 (代聊)]
                                             • 悬浮窗实时透视 HR 意图   • 鹿鹿按预设策略代聊
                                             • 现成高情商话术一键填入   • 拿到面试通知再交接
```

</div>

### 🔥 E人·极速推土机模式 (Fast Auto-Pilot)
> *“只要结果，今天必须聊够 100 个 HR！”*
* **全自动无人值守闭环**：自动巡查列表 -> 深度洗净 JD -> 大模型智能评估 -> 生成定制化破冰词并主动发起沟通。
* **HR 活跃度过滤**：自动识别“刚刚活跃/今日活跃”，坚决不把时间浪费在死尸岗位上。

### 🌸 I人·治愈系护航模式 (Gentle & Copilot)
> *“我还没准备好，害怕说错话被拒，需要先做心理建设……”*
* **鹿鹿精选小纸条**：将匹配度 > 85 分的岗位存入待办库，附上软糯的推荐理由与避雷提醒（“这家双休无打卡，HR 态度超温柔哦~”）。
* **悬浮窗（HUD）意图透视**：HR 发消息时，悬浮窗实时分析对方意图（“别紧张，对方只是例行做稳定性调查”）。
* **一键高情商话术助攻**：自动生成 3 种不同风格的回答（高情商版 / 礼貌温和版 / 巧妙防守版），点击一键填入输入框，彻底消灭社恐打字焦虑。

---

## 🏗️ 系统架构设计 (Clean Architecture & Multi-Agent)

鹿鹿采用高内聚、低耦合的模块化分层架构，各智能体协同运作，由调度中枢与有限状态机（FSM）严格控制：

```text
app/src/main/java/com/agent/boss/
├── accessibility/         // 【底层驱动层】DFS节点遍历回收 (防OOM)、贝塞尔拟人手势引擎
├── dispatcher/            // 【调度中枢层】有限状态机 (FSM)、广播解耦与事件总线契约
├── agent/                 // 【多智能体集群】
│   ├── supervisor/        // 🛡️ 安全风控官：验证码拦截、频率节流熔断
│   ├── scout/             // 🔍 感知工兵：页面巡查、JD 文本清洗分段
│   ├── evaluator/         // 📊 评估参谋：本地 0-Token 过滤 + DeepSeek 深度契合度评分
│   └── communicator/      // 💬 谈判代表：意图识别、定制开场白生成、悬浮窗话术助攻
├── llm/                   // 【认知支撑层】DeepSeek 客户端、JSON Schema 解析、Token 审计
├── floating/              // 【悬浮呈现层】双模态 HUD (灵动胶囊小药丸 + 极客大控制台)
└── data/                  // 【持久化层】Room 数据库 (岗位去重库)、EncryptedDataStore (加密配置)
```

---

## 📱 悬浮窗交互体验 (Floating HUD)

鹿鹿拥有自研的 **双模态自适应悬浮窗**：
- 🟢 **灵动胶囊模式（Capsule）**：贴边自动吸附，以呼吸灯状态静默展示 Agent 当前步骤（扫描中 / 思考中 / 沟通中），支持手势触摸穿透。
- 🖥️ **极客控制台模式（Console）**：展开后以打字机流式实时呈现 DeepSeek 的思考链、风险评估打分及实时系统日志，支持一键急停。

---

## 🚀 极速上手 (Zero Config, Ready to Use)

不同于需要配置 Python、Playwright、NodeJS 的传统脚本，鹿鹿**无需任何电脑端环境**：

1. **下载安装**：前往 [Releases 页面](../../releases) 下载最新的 `Lulu-Release.apk` 安装到 Android 手机。
2. **开启权限**：首次启动时，根据向导开启 **无障碍权限（Accessibility）**、**悬浮窗权限** 与 **电池优化白名单**。
3. **填入 API Key**：在设置页填入你的 `DeepSeek API Key`（支持本地联通性一键 Ping 测试）。
4. **粘贴简历**：粘贴你的 Markdown 格式简历，选择 **E人模式** 或 **I人模式**，点击 **【启动鹿鹿】** 即可开启智能托管！

---

## 📊 开源社区版 vs Pro 增值服务

鹿鹿坚持**核心功能完全开源、永不阉割**。为了支撑高阶 SOTA 模型算力研发，我们提供清晰透明的增值选项：

| 功能特性 | 社区开源版 (Free) | Pro 深度备战版 (增值服务) |
| :--- | :---: | :---: |
| **全自动无障碍投递 (E人流)** | ✅ 终身免费 (自备 Key) | ✅ 支持 |
| **I人模式 (精选日报 + 话术参谋)** | ✅ 支持 (基础模式) | ✅ 支持 |
| **拟人化贝塞尔防封手势** | ✅ 支持 | ✅ 支持 |
| **360° SOTA 深度面试押题与 STAR 话术** | ❌ (基础概要) | ✅ **无限次深度穿透拆解 (¥29.9/月 或 ¥9.9/单次)** |
| **云端开箱即用免配置专线** | ❌ (需自备 Key) | ✅ 内置高速专线算力包 |
| **基于目标 JD 的 SOTA 简历重塑报告** | ❌ | ✅ 深度差距诊断与量化修改建议 |

---

## ⚠️ 免责声明 (Disclaimer)

1. 本项目仅供 **Android 无障碍技术研究、大模型端侧 Agent 架构探索及个人求职效率提升** 使用，严禁用于任何商业牟利、黑产刷量或恶意滥用行为。
2. 请严格遵守目标招聘平台的服务协议，合理设置投递频率与沟通上限。使用本工具所产生的一切账号风控、限制或法律后果由使用者自行承担，开发者不承担任何直接或连带责任。
3. 本项目为纯端侧应用，所有简历、隐私及 API Key 均保留在用户手机本地，不会向任何未授权的第三方服务器上传个人隐私。

---

## 🤝 贡献与感谢

欢迎提交 Issue 与 Pull Request！无论是适配更多平台（LinkedIn / 猎聘 / 智联），还是优化鹿鹿的治愈系提示词，都非常期待你的加入！

- **LLM Engine**: [DeepSeek](https://www.deepseek.com/)
- **UI Architecture**: Jetpack & Material Components
- **Automation**: Android Accessibility APIs

<div align="center">
  <sub>Made with ❤️ by Developers, for Every Job Hunter. 祝每一位求职者都能早日斩获心仪 Offer！</sub>
</div>