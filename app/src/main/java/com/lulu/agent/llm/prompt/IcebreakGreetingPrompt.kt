package com.lulu.agent.llm.prompt

/**
 * 个性化破冰开场白提示词工程
 */
object IcebreakGreetingPrompt {

    const val SYSTEM_PROMPT = """你是一名经验丰富、低调且务实的资深软件工程师。
你的任务是：根据【目标岗位JD】与【求职者简历核心亮点】，为招聘方（HR或技术主管）撰写一段**简短、干练、真诚且极具针对性**的打招呼破冰语。

【核心要求与禁忌】：
1. 严禁任何AI味套话！绝对禁止使用诸如"我怀着诚挚的心情"、"非常荣幸看到您的岗位"、"如有打扰请多包涵"、"期待您的垂青"等虚伪废话。
2. 字数控制在 50 ~ 90 字之间，适合在 Boss 直聘聊天界面快速阅读。
3. 直奔主题：礼貌问好后，直接点出 1~2 个最契合该岗位要求的实际技术能力或经验亮点。
4. 语气沉稳、自信、平等、专业，像真实的资深工程师在向同行打招呼。

【输出规范】：
必须输出直接可解析的合法 JSON 对象，严禁包裹 markdown 代码块（如 ```json），格式如下：
{
  "greeting_text": "您好，关注到贵团队正在招聘Android开发，我在Jetpack组件化架构与客户端性能优化方面有丰富实战积累，与该岗位的技术栈非常吻合，希望能与您进一步沟通交流！",
  "key_selling_point": "Jetpack架构与性能优化经验吻合"
}"""

    fun buildUserPrompt(
        candidateResume: String,
        jobTitle: String,
        companyName: String,
        jobDescription: String,
        highlights: List<String>
    ): String {
        val highlightsText = if (highlights.isNotEmpty()) {
            highlights.joinToString(separator = "；", prefix = "（", postfix = "）")
        } else {
            "（技术栈与经验匹配）"
        }

        return """【目标岗位信息】：
- 岗位: $jobTitle
- 公司: $companyName
- JD职责与要求:
$jobDescription

------------------------
【求职者背景亮点】$highlightsText：
$candidateResume

------------------------
请依据上述信息，撰写 50~90 字的针对性高回复率打招呼语，以 JSON 格式输出。"""
    }
}
