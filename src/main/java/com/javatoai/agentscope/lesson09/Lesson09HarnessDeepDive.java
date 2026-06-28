package com.javatoai.agentscope.lesson09;

import com.javatoai.agentscope.support.AgentConfig;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.support.StreamEventPrinter;
import com.javatoai.agentscope.tool.ProgressTools;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.MemoryConfig.FlushTrigger;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;

import java.time.Duration;
import java.util.List;

/**
 * Lesson 09 — HarnessAgent 深度拆解。
 *
 * <p>HarnessAgent = ReActAgent + Workspace + 自动持久化 + Compaction + 长期记忆 + 能力开关。
 *
 * <p>本课按 6 个阶段逐步打开 HarnessAgent 的每一层：
 * <ol>
 *   <li><b>Workspace 人设</b> — AGENTS.md 文件驱动 personality</li>
 *   <li><b>自动会话持久化</b> — 不需要手动配 JsonFileAgentStateStore</li>
 *   <li><b>TaskList 集成</b> — enableTaskList 自动注入 Todo 上下文</li>
 *   <li><b>长期记忆 (Memory)</b> — 跨 session 记住用户信息，FlushTrigger 控制写入</li>
 *   <li><b>对话压缩 (Compaction)</b> — 超长对话自动摘要</li>
 *   <li><b>流式输出</b> — HarnessAgent 同样支持 streamEvents</li>
 * </ol>
 *
 * <p>运行前确保 workspace 目录下有 AGENTS.md（项目已有 .agentscope/workspace/AGENTS.md）。
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson09.Lesson09HarnessDeepDive
 * }</pre>
 */
public final class Lesson09HarnessDeepDive {

    private static final String AGENT_NAME = "harness-learner";
    private static final String SESSION_ID = "lesson09-session";

    private static final String SYSTEM_PROMPT = """
            你是一个 AgentScope 学习助手。
            当用户询问学习进度时，使用 record_progress / check_progress 工具。
            当用户让你做计划时，使用 todo_write 工具。
            用中文回复，保持简洁。
            """;

    private Lesson09HarnessDeepDive() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 09 · HarnessAgent 深度拆解",
                "Workspace + 自动持久化 + TaskList + Memory + Compaction + 流式");

        phase1WorkspacePersonality();
        phase2AutoPersistence();
        phase3TodoTaskList();
        phase4MemoryControl();
        phase5Compaction();
        phase6StreamingWithHarness();

        System.out.println();
        System.out.println(">>> Lesson 09 完成：你已理解 HarnessAgent 各层能力及其与 ReActAgent 的差异。");
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 1: Workspace 人设
    // ════════════════════════════════════════════════════════════════════

    /**
     * <h3>Workspace 机制</h3>
     *
     * <p>HarnessAgent 启动时会读取 {@code workspace/AGENTS.md} 作为"人设文件"。
     * 这个文件的内容会被注入到 system prompt 中，与 {@code .sysPrompt()} 合并。
     *
     * <p>项目已有的 {@code .agentscope/workspace/AGENTS.md} 内容：
     * <pre>
     * # note-taker
     * You are a friendly note-taking assistant...
     * </pre>
     *
     * <p>Workspace 目录结构：
     * <pre>
     * .agentscope/workspace/
     *   AGENTS.md          ← Agent 人设
     *   MEMORY.md          ← 长期记忆（自动写入）
     *   memory/            ← 记忆文件
     *   sessions/          ← 会话状态
     *   skills/            ← 技能脚本
     *   knowledge/         ← 知识文件
     * </pre>
     */
    private static void phase1WorkspacePersonality() {
        printPhase("Phase 1", "Workspace 人设 — AGENTS.md 驱动 personality");

        // 注意：HarnessAgent 不需要 .stateStore(...)，自动持久化
        HarnessAgent agent = HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)   // ← 关键：指向 workspace 目录
                .toolkit(buildToolkit())
                .build();

        RuntimeContext ctx = buildContext();

        // 第一轮：让 Agent 做自我介绍，验证 AGENTS.md 是否生效
        Msg reply = agent.call(
                new UserMessage("请用一句话介绍你自己，然后记录我的学习进度：正在学习 HarnessAgent（session_id=" + SESSION_ID + "）"),
                ctx).block();

        System.out.println("[回复] " + reply.getTextContent());
        System.out.println();

        agent.close();
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 2: 自动会话持久化
    // ════════════════════════════════════════════════════════════════════

    /**
     * <h3>自动持久化</h3>
     *
     * <p>ReActAgent 需要手动配 {@code JsonFileAgentStateStore}，而 HarnessAgent 自动完成。
     * 只要传入相同的 (userId, sessionId)，第二轮 call 就能记住第一轮的对话。
     *
     * <p>对比：
     * <pre>
     * // ReActAgent（手动）
     * .stateStore(new JsonFileAgentStateStore(Path.of(...)))
     *
     * // HarnessAgent（自动）
     * .workspace(AgentConfig.WORKSPACE_PATH)  // 就这一行
     * </pre>
     *
     * <p>状态文件自动保存到：
     * <pre>
     * .agentscope/workspace/agents/{agentName}/context/{agentName}/agent_state.json
     * </pre>
     */
    private static void phase2AutoPersistence() {
        printPhase("Phase 2", "自动会话持久化 — 同一 session 跨 call 记忆");

        // 第一轮 call
        HarnessAgent agent1 = HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(buildToolkit())
                .build();

        RuntimeContext ctx = buildContext();

        Msg turn1 = agent1.call(
                new UserMessage("我叫小明，我最喜欢的 AgentScope 模块是 Middleware。请用 record_progress 记录（session_id=" + SESSION_ID + "，topic=Middleware，status=in_progress）"),
                ctx).block();
        System.out.println("[第1轮] " + turn1.getTextContent());
        agent1.close();

        // 第二轮 call — 新 Agent 实例，相同的 RuntimeContext
        // 框架自动从 workspace 恢复之前的对话状态
        HarnessAgent agent2 = HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(buildToolkit())
                .build();

        Msg turn2 = agent2.call(
                new UserMessage("我叫什么名字？我在学什么模块？请用 check_progress 确认（session_id=" + SESSION_ID + "）"),
                ctx).block();
        System.out.println("[第2轮] " + turn2.getTextContent());
        System.out.println();
        agent2.close();
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 3: TaskList 集成
    // ════════════════════════════════════════════════════════════════════

    /**
     * <h3>enableTaskList</h3>
     *
     * <p>{@code .enableTaskList(true)} 会：
     * <ol>
     *   <li>自动注册 {@link TodoTools}（不需要手动 registerTool）</li>
     *   <li>注入 TaskReminderMiddleware：每次对话自动提醒 Agent 当前任务列表</li>
     * </ol>
     *
     * <p>任务文件保存在 workspace 的 tasks/ 目录下。
     */
    private static void phase3TodoTaskList() {
        printPhase("Phase 3", "TaskList 集成 — enableTaskList 自动管理 Todo");

        HarnessAgent agent = HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(buildToolkit())
                .enableTaskList(true)    // ← 自动注入 TodoTools + TaskReminderMiddleware
                .build();

        RuntimeContext ctx = buildContext();

        // 第一轮：制定 3 步学习计划
        Msg turn1 = agent.call(
                new UserMessage("请帮我制定一个 3 步的 HarnessAgent 学习计划，用 todo_write 记录下来。"),
                ctx).block();
        System.out.println("[第1轮] " + turn1.getTextContent());
        System.out.println();

        // 第二轮：继续对话，Agent 会自动看到之前的 Todo
        Msg turn2 = agent.call(
                new UserMessage("我们学到哪了？接下来该做什么？"),
                ctx).block();
        System.out.println("[第2轮] " + turn2.getTextContent());
        System.out.println();

        agent.close();
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 4: 长期记忆 (Memory) 控制
    // ════════════════════════════════════════════════════════════════════

    /**
     * <h3>Memory 机制 — FlushTrigger 控制写入</h3>
     *
     * <p>HarnessAgent 的 Memory 功能会在对话中自动识别用户个人信息，
     * 写入 {@code workspace/{userId}/MEMORY.md}。
     *
     * <p>但有时你不想每次都写（浪费 token、不需要记），用 {@link FlushTrigger} 控制：
     *
     * <pre>
     * FlushTrigger.always()           — 默认，每次 call 结束都尝试写
     * FlushTrigger.never()            — 永不自动写 Memory
     * FlushTrigger.throttled(d)       — 限流，d 时间内最多写一次
     * </pre>
     *
     * <p>另外还可用 {@code .disableMemoryHooks(true)} 彻底关掉 Memory 功能。
     */
    private static void phase4MemoryControl() {
        printPhase("Phase 4", "Memory 控制 — FlushTrigger 三种模式对比");

        // ── 子阶段 A: FlushTrigger.never —— 不写 Memory ──
        System.out.println("  [A] FlushTrigger.never — 不自动写 Memory");
        HarnessAgent agentNever = HarnessAgent.builder()
                .name(AGENT_NAME + "-nomem")
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(buildToolkit())
                .memory(MemoryConfig.builder()
                        .flushTrigger(FlushTrigger.never())   // ← 不写
                        .build())
                .build();

        RuntimeContext ctxNever = RuntimeContext.builder()
                .userId(AgentConfig.DEMO_USER_ID)
                .sessionId(SESSION_ID + "-nomem")
                .build();

        Msg replyA = agentNever.call(
                new UserMessage("我叫张三，我最喜欢的框架是 Spring。"
                        + "请用 record_progress 记录（session_id=" + SESSION_ID + "-nomem，topic=Spring，status=started）"),
                ctxNever).block();
        System.out.println("  [回复] " + replyA.getTextContent());
        System.out.println("  → Memory 不会被写入（FlushTrigger.never）");
        agentNever.close();
        System.out.println();

        // ── 子阶段 B: FlushTrigger.throttled —— 限流写 Memory ──
        System.out.println("  [B] FlushTrigger.throttled(10分钟) — 10 分钟内最多写一次");
        HarnessAgent agentThrottled = HarnessAgent.builder()
                .name(AGENT_NAME + "-throttled")
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(buildToolkit())
                .memory(MemoryConfig.builder()
                        .flushTrigger(FlushTrigger.throttled(Duration.ofMinutes(10)))
                        .build())
                .build();

        RuntimeContext ctxThrottled = RuntimeContext.builder()
                .userId(AgentConfig.DEMO_USER_ID)
                .sessionId(SESSION_ID + "-throttled")
                .build();

        // 短时间内两次 call — 只有第一次会触发写 Memory
        Msg replyB1 = agentThrottled.call(
                new UserMessage("我叫李四，我正在学习 AgentScope。请记录进度（session_id=" + SESSION_ID + "-throttled，topic=AgentScope，status=in_progress）"),
                ctxThrottled).block();
        System.out.println("  [第1次 call] " + replyB1.getTextContent());
        System.out.println("  → 第1次：距上次写入超过 10 分钟，触发写入 ✓");

        Msg replyB2 = agentThrottled.call(
                new UserMessage("刚才我叫什么名字？在学什么？"),
                ctxThrottled).block();
        System.out.println("  [第2次 call] " + replyB2.getTextContent());
        System.out.println("  → 第2次：间隔太短，被 throttled 跳过 ✗");
        agentThrottled.close();

        System.out.println();
        System.out.println("  提示：查看 Memory 文件 → .agentscope/workspace/"
                + AgentConfig.DEMO_USER_ID + "/MEMORY.md");
        System.out.println();
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 5: 对话压缩 (Compaction)
    // ════════════════════════════════════════════════════════════════════

    /**
     * <h3>Compaction 机制</h3>
     *
     * <p>当对话历史达到 {@code triggerMessages} 或 {@code triggerTokens} 阈值时，
     * 框架自动用模型生成摘要替换旧消息，控制上下文窗口。
     *
     * <p>关键参数：
     * <pre>
     * .triggerMessages(20)      — 消息数超过 20 触发
     * .triggerTokens(4000)      — 或 token 数超过 4000 触发
     * .keepMessages(5)          — 压缩后保留最近 5 条
     * .keepTokens(2000)         — 或保留 2000 token
     * </pre>
     *
     * <p>两种使用方式：
     * <ol>
     *   <li>显式传入 {@link CompactionConfig}</li>
     *   <li>用 {@code .disableCompaction(true)} 彻底关闭</li>
     * </ol>
     */
    private static void phase5Compaction() {
        printPhase("Phase 5", "Compaction 对话压缩 — 控制上下文窗口");

        // ── 对比: 显式 Compaction vs 关闭 Compaction ──
        System.out.println("  [A] 显式 CompactionConfig — 短阈值便于演示");
        HarnessAgent agentCompact = HarnessAgent.builder()
                .name(AGENT_NAME + "-compact")
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(buildToolkit())
                .compaction(CompactionConfig.builder()
                        .triggerMessages(8)     // 8 条消息就触发（演示用，生产建议 30+）
                        .keepMessages(3)        // 保留最近 3 条
                        .build())
                .build();

        RuntimeContext ctxCompact = RuntimeContext.builder()
                .userId(AgentConfig.DEMO_USER_ID)
                .sessionId(SESSION_ID + "-compact")
                .build();

        // 多轮短对话，快速积累消息数来触发 compaction
        String[][] fastTalks = {
                {"我叫小王", "你好！"},
                {"今天天气如何？", "可以用工具查。"},
                {"什么是 Middleware？", "是洋葱模型拦截器。"},
                {"ReActAgent 的核心循环是什么？", "Reason → Act → 回复。"},
        };
        for (String[] pair : fastTalks) {
            agentCompact.call(new UserMessage(pair[0]), ctxCompact).block();
            System.out.println("  .");
        }
        System.out.println("  → 已发起 " + (fastTalks.length * 2) + "+ 条消息，触发 compaction");
        agentCompact.close();
        System.out.println();

        // ── 子阶段 B: 关闭 Compaction ──
        System.out.println("  [B] disableCompaction(true) — 永不压缩");
        HarnessAgent agentNoCompact = HarnessAgent.builder()
                .name(AGENT_NAME + "-nocompact")
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(buildToolkit())
                .disableCompaction()   // ← 关闭压缩
                .build();

        // 同一 sessionId 先写后读，验证全量历史都在（未压缩）
        RuntimeContext ctxNc = RuntimeContext.builder()
                .userId(AgentConfig.DEMO_USER_ID)
                .sessionId(SESSION_ID + "-nocompact")
                .build();

        agentNoCompact.call(
                new UserMessage("我最喜欢的编程语言是 Java。请记录进度（session_id=" + SESSION_ID + "-nocompact，topic=Java，status=in_progress）"),
                ctxNc).block();

        Msg reply = agentNoCompact.call(
                new UserMessage("我最喜欢什么编程语言？"),
                ctxNc).block();
        System.out.println("  [回复] " + reply.getTextContent());
        System.out.println("  → 全量历史保留，未压缩，Agent 能记住细节");
        agentNoCompact.close();
        System.out.println();
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 6: HarnessAgent 的流式输出
    // ════════════════════════════════════════════════════════════════════

    /**
     * <h3>HarnessAgent 也支持 streamEvents</h3>
     *
     * <p>用法与 ReActAgent 完全相同，但事件流中会多出 harness 层的处理
     * （compaction、memory flush 等中间件逻辑）。
     */
    private static void phase6StreamingWithHarness() {
        printPhase("Phase 6", "HarnessAgent 流式输出");

        HarnessAgent agent = HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(buildToolkit())
                .build();

        RuntimeContext ctx = buildContext();

        System.out.print("[流式] ");
        agent.streamEvents(
                new UserMessage("用 2 句话总结 HarnessAgent 和 ReActAgent 的区别。"),
                ctx)
                .doOnNext(StreamEventPrinter::handleEvent)
                .blockLast();
        System.out.println();
        System.out.println();

        agent.close();
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    private static Toolkit buildToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ProgressTools());
        return toolkit;
    }

    private static RuntimeContext buildContext() {
        return RuntimeContext.builder()
                .userId(AgentConfig.DEMO_USER_ID)
                .sessionId(SESSION_ID)
                .build();
    }

    private static void printPhase(String phase, String desc) {
        System.out.println("-".repeat(60));
        System.out.println("  " + phase + " · " + desc);
        System.out.println("-".repeat(60));
    }
}
