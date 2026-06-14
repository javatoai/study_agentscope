# AgentScope Java 2.0 学习案例

从零上手 [AgentScope Java 2.0](https://java.agentscope.io/v2/en/docs/index.html) 的渐进式 Maven 项目。

## 环境要求

| 项目 | 版本 |
|------|------|
| JDK | 17+（本项目使用 21） |
| Maven | 3.9+ |
| API Key | 智谱 GLM（`API_KEY_ZAI`，与 `study-langchain` 相同） |

## 快速开始

```powershell
# 1. 进入项目目录
cd Q:\workspace_idea\study_agentscope

# 2. 无需额外设置 Key —— 与 study-langchain 共用 API_KEY_ZAI
#    若未配置：$env:API_KEY_ZAI="your-key"

# 3. 编译
mvn -q compile

# 4. 从第一课开始
mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson01.Lesson01BasicReAct
```

## 学习路线（7 课）

```
Lesson 01  ReActAgent 核心           call() + ModelRegistry
    ↓
Lesson 02  HarnessAgent 工程层       Workspace + 会话持久化 + compaction
    ↓
Lesson 03  streamEvents 流式事件     类型化 AgentEvent
    ↓
Lesson 04  自定义 @Tool              Reason → Act 闭环
    ↓
Lesson 05  多用户 / 多会话           HarnessAgent + RuntimeContext 隔离
    ↓
Lesson 06  结构化输出                call(..., StudySummary.class)
    ↓
Lesson 07  RuntimeContext 注入       put(Class, POJO) → @Tool 参数
```

## 各课运行命令

| 课 | 主类 | 是否需要 API |
|----|------|-------------|
| 01 | `com.javatoai.agentscope.lesson01.Lesson01BasicReAct` | 是 |
| 02 | `com.javatoai.agentscope.lesson02.Lesson02HarnessAgent` | 是 |
| 03 | `com.javatoai.agentscope.lesson03.Lesson03Streaming` | 是 |
| 04 | `com.javatoai.agentscope.lesson04.Lesson04CustomTool` | 是 |
| 05 | `com.javatoai.agentscope.lesson05.Lesson05MultiUserSession` | 是 |
| 06 | `com.javatoai.agentscope.lesson06.Lesson06StructuredOutput` | 是 |
| 07 | `com.javatoai.agentscope.lesson07.Lesson07RuntimeContext` | 是 |

示例：

```powershell
mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson05.Lesson05MultiUserSession
```

## 项目结构

```
src/main/java/com/javatoai/agentscope/
├── support/          # AgentConfig、EnvSupport、UserContext
├── model/            # 结构化输出 Schema（StudySummary）
├── tool/             # @Tool 工具类（TimeTools、UserTools）
├── lesson01/         # Basic ReActAgent
├── lesson02/         # HarnessAgent
├── lesson03/         # streamEvents
├── lesson04/         # Custom Tool
├── lesson05/         # Multi-user session
├── lesson06/         # Structured output
└── lesson07/         # RuntimeContext injection

.agentscope/workspace/AGENTS.md   # Harness 人设文件
```

## 2.0 核心概念

| 概念 | 说明 |
|------|------|
| **ReActAgent** | 推理-行动循环核心，适合原型与轻量场景 |
| **HarnessAgent** | 生产级封装：workspace、memory、compaction、subagent、sandbox |
| **RuntimeContext** | 单次 call 的 userId / sessionId / 业务 POJO |
| **AgentStateStore** | 对话状态持久化（默认本地 JSON，生产可换 Redis） |
| **Workspace** | Agent 身份目录：`AGENTS.md`、`MEMORY.md`、`skills/` |
| **streamEvents** | 类型化事件流，支持 HITL、权限确认、工具进度 |

## 模型配置（与 study-langchain 对齐）

与 `Q:\workspace_pycharm\study-langchain\llm_config.py` 使用同一套配置：

| 项 | 值 |
|----|-----|
| 环境变量 | `API_KEY_ZAI` |
| API 地址 | `https://open.bigmodel.cn/api/paas/v4/` |
| 默认模型 | `zai:glm-5`（对应 langchain 的 `glm-5`） |
| 配置类 | `support/ModelSupport.java` |

langchain 项目里 Key **没有写死在代码中**，而是通过 `os.getenv("API_KEY_ZAI")` 读取；AgentScope 项目同样只读环境变量，不会把密钥提交到 Git。

```powershell
# 若系统里尚未设置（与 langchain PyCharm 运行配置相同）
$env:API_KEY_ZAI="your-key"
```

## 切换其他模型提供商

修改 `AgentConfig.DEFAULT_MODEL` 或 `ModelSupport` 中的注册逻辑：

| 模型字符串 | 环境变量 |
|-----------|---------|
| `zai:glm-5` | `API_KEY_ZAI`（**当前默认，与 langchain 一致**） |
| `dashscope:qwen-plus` | `DASHSCOPE_API_KEY` |
| `deepseek:deepseek-chat` | 需在 `ModelSupport` 中自行注册 DeepSeek 工厂 |
| `openai:gpt-4o` | `OPENAI_API_KEY` |
| `anthropic:claude-sonnet-4-5` | `ANTHROPIC_API_KEY` |
| `ollama:llama3` | （本地 Ollama） |

## 进阶资源

- [Quickstart](https://java.agentscope.io/v2/en/docs/quickstart.html)
- [Agent API](https://java.agentscope.io/v2/en/docs/building-blocks/agent.html)
- [Harness 架构](https://java.agentscope.io/v2/en/docs/harness/architecture.html)
- [GitHub 示例](https://github.com/agentscope-ai/agentscope-java)
