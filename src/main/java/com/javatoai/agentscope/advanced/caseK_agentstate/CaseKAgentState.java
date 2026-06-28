package com.javatoai.agentscope.advanced.caseK_agentstate;

import com.javatoai.agentscope.support.AgentConfig;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * 案例 K —— AgentState 深度教学。
 *
 * <p>AgentState 是整个 ReActAgent 运行时的唯一可变状态体，存着
 * 对话在多次 call() 之间累积的一切数据。
 *
 * <p>本案例分为五个学习阶段：
 * <ol>
 *   <li><b>AgentState 内部结构</b> — 9 个字段逐一讲解</li>
 *   <li><b>AgentState 的生命周期</b> — fresh vs loaded vs persisted</li>
 *   <li><b>contextMutable（对话列表）</b> — Agent "记忆" 的唯一来源</li>
 *   <li><b>四个 Session 后端</b> — InMemory vs JsonFile vs Redis vs MySQL</li>
 *   <li><b>多 call 持久化验证</b> — 同一个 sessionId 两次 call，对话记忆不丢</li>
 * </ol>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseK_agentstate.CaseKAgentState
 * }</pre>
 */
public final class CaseKAgentState {

    private CaseKAgentState() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case K · AgentState 深度教学",
                "内部结构 → 生命周期 → 四种Store → 多call持久化");

        // ══════════════════════════════════════════════════════════
        // 阶段 1: AgentState 内部结构
        // ══════════════════════════════════════════════════════════
        stage1_agentStateStructure();

        // ══════════════════════════════════════════════════════════
        // 阶段 2: AgentState 生命周期
        // ══════════════════════════════════════════════════════════
        stage2_lifecycle();

        // ══════════════════════════════════════════════════════════
        // 阶段 3: contextMutable — 对话记忆
        // ══════════════════════════════════════════════════════════
        stage3_contextMutable();

        // ══════════════════════════════════════════════════════════
        // 阶段 4: 四种 Session 后端对比
        // ══════════════════════════════════════════════════════════
        stage4_sessionBackends();

        // ══════════════════════════════════════════════════════════
        // 阶段 5: 多 call 持久化验证
        // ══════════════════════════════════════════════════════════
        stage5_persistenceDemo();
    }

    // ────────────────────────────────────────────────────────────
    // 阶段 1: AgentState 内部结构
    // ────────────────────────────────────────────────────────────

    static void stage1_agentStateStructure() {
        System.out.println("━".repeat(60));
        System.out.println("  阶段 1: AgentState 内部结构 — 9 个字段");
        System.out.println("━".repeat(60));
        System.out.println();

        System.out.println("""
                AgentState 是 ReActAgent 运行时唯一可变的状态容器。
                它在首次 call() 时由 freshState() 创建，每次 call() 结束时
                通过 saveStateToSession() 持久化。

                ┌────────────────── AgentState ────────────────────────────┐
                │                                                          │
                │  sessionId          — 会话主键（与 RuntimeContext 对齐）  │
                │  summary            — 对话总结（compaction 后自动更新）   │
                │  context (List<Msg>)— ★ 对话消息列表 ★                  │
                │  replyId            — 最后一轮 assistant 消息的 ID       │
                │  curIter            — 当前 ReAct 迭代轮次                │
                │  shutdownInterrupted — 是否被优雅关闭中断过               │
                │                                                          │
                │  permissionContext  — 权限规则（allow/deny/ask 三套）    │
                │  toolContext        — 工具缓存 + 激活的 toolGroup        │
                │  tasksContext       — 任务列表（TaskReminderMiddleware） │
                │  planModeContext    — Plan Mode 状态                     │
                │                                                          │
                └──────────────────────────────────────────────────────────┘

                核心设计原则:
                  - 开发者禁止直接操作 AgentState，框架内部管理
                  - 开发者通过 call() 输入消息，框架自动 append
                  - 持久化由 Session 接口抽象，后端可插拔
                """);

        System.out.println("字段详细说明:");
        printFieldTable();
        System.out.println();
    }

    private static void printFieldTable() {
        Object[][] rows = {
                {"sessionId", "String", "会话主键，与 RuntimeContext.sessionId 对齐",
                        "getState().getSessionId()"},
                {"summary", "String", "对话压缩后的摘要（CompactionMiddleware 写入）",
                        "getState().getSummary()"},
                {"context", "List<Msg>", "★ 所有对话消息（system/user/assistant/toolResult）",
                        "getState().contextMutable()"},
                {"replyId", "String", "最后一轮 assistant 消息的 Msg.id",
                        "getState().getReplyId()"},
                {"curIter", "int", "当前 ReAct 迭代轮次（0-based）",
                        "getState().getCurIter()"},
                {"shutdownInterrupted", "boolean", "JVM 优雅关闭时是否中断过此 Agent",
                        "getState().isShutdownInterrupted()"},
                {"permissionContext", "PermissionContextState", "工具权限：mode + allowRules + denyRules + askRules",
                        "getState().getPermissionContext()"},
                {"toolContext", "ToolContextState", "文件读缓存 + 激活的 toolGroup 列表",
                        "getState().getToolContext()"},
                {"tasksContext", "TaskContextState", "任务列表（enableTaskList=true 时自动管理）",
                        "getState().getTasksContext()"},
                {"planModeContext", "PlanModeContextState", "Plan Mode 是否激活 + 当前计划文件路径",
                        "getState().getPlanModeContext()"},
        };

        String fmt = "  %-22s %-18s %-44s %s%n";
        System.out.printf(fmt, "字段", "类型", "用途", "访问方式");
        System.out.printf(fmt, "──────", "────", "────", "────────");
        for (Object[] row : rows) {
            System.out.printf(fmt, row[0], row[1], row[2], row[3]);
        }
    }

    // ────────────────────────────────────────────────────────────
    // 阶段 2: AgentState 生命周期
    // ────────────────────────────────────────────────────────────

    static void stage2_lifecycle() {
        System.out.println("━".repeat(60));
        System.out.println("  阶段 2: AgentState 生命周期");
        System.out.println("━".repeat(60));
        System.out.println();

        System.out.println("""
                AgentState 的生命周期 = Agent 从首次调用到会话废弃的完整过程:

                ┌─── 第 1 次 call(msg, ctx) ──────────────────────────┐
                │                                                     │
                │  beforeAgentExecution():                            │
                │    1. 从 ctx 中取出 pendingRuntimeContext            │
                │    2. 绑定 userId/sessionId → SessionKey             │
                │    3. 调用 loadOrCreateAgentState(session, key, ...) │
                │       │                                             │
                │       ├─ Session 中有此 key?                         │
                │       │   ├─ YES → load 已有 AgentState ✅          │
                │       │   └─ NO  → freshState() 创建全新 AgentState │
                │       │            state.context = []  (空列表)      │
                │                                                     │
                │  doCallFn.apply(msgs):                              │
                │    ReAct 循环:                                      │
                │      state.contextMutable().add(systemMsg)          │
                │      state.contextMutable().add(userMsg)            │
                │      state.contextMutable().add(assistantMsg)       │
                │      state.contextMutable().add(toolResultMsg)      │
                │      ...                                            │
                │                                                     │
                │  saveStateToSession():                              │
                │    session.save(sessionKey, "agentState", state)    │
                │    → 持久化（取决于 Session 后端）                   │
                └─────────────────────────────────────────────────────┘

                ┌─── 第 2 次 call(msg, ctx) (相同 sessionId) ──────────┐
                │                                                     │
                │  beforeAgentExecution():                            │
                │    → Session 中有此 key ✅                           │
                │    → load 出上次保存的 AgentState                    │
                │    → state.context 里已有上轮的所有消息               │
                │    → Agent "记住"了上一轮对话！                       │
                │                                                     │
                └─────────────────────────────────────────────────────┘

                关键结论:
                  • Agent 实例是无状态的（单例 Bean）
                  • AgentState 是有状态的（按 sessionId 存储）
                  • 同一实例 + 不同 sessionId = 完全隔离的对话
                """);
    }

    // ────────────────────────────────────────────────────────────
    // 阶段 3: contextMutable — 对话记忆
    // ────────────────────────────────────────────────────────────

    static void stage3_contextMutable() {
        System.out.println("━".repeat(60));
        System.out.println("  阶段 3: contextMutable — Agent 记忆的唯一来源");
        System.out.println("━".repeat(60));
        System.out.println();

        System.out.println("""
                Agent 没有任何"隐式记忆"——它不会记在脑子里，只记在
                state.context 这个 List<Msg> 里。

                每轮 ReAct 循环向 LLM 发消息时，框架会组装:
                    [currentSystemMsg]
                  + state.context           ← 全部历史
                  + [本轮新的 UserMessage]

                这意味着:
                  1. 每次 LLM 调用都带着完整的历史消息
                  2. 消息越多 → token 消耗越大 → 需要 Compaction
                  3. context 的顺序就是时间顺序:
                     system → user → assistant → toolResult → user → ...

                Agent 眼中的"记忆"其实就是这段消息列表:

                ┌────────── state.context ──────────────────────────┐
                │ index | role       | 内容                           │
                │   0   | system     | 你是一个学习教练...             │
                │   1   | user       | 我叫小王                        │
                │   2   | assistant  | 好的小王，我记住了...           │
                │   3   | user       | 我叫什么？                      │
                │   4   | assistant  | 你叫小王 ← 从 index-1 读到的   │
                └────────────────────────────────────────────────────┘

                context 操作的两个接口:

                getContext(): 返回不可变视图 (Collections.unmodifiableList)
                  框架组装 LLM 请求时用这个 —— 只读

                contextMutable(): 返回可变引用
                  框架内部 append 新消息时用 —— add()
                """);
    }

    // ────────────────────────────────────────────────────────────
    // 阶段 4: 四种 Session 后端
    // ────────────────────────────────────────────────────────────

    static void stage4_sessionBackends() {
        System.out.println("━".repeat(60));
        System.out.println("  阶段 4: Session — AgentState 的持久化抽象");
        System.out.println("━".repeat(60));
        System.out.println();

        System.out.println("""
                AgentStateStore 接口 = AgentState 的 DAO 层。四个内置实现:

                ┌──────────────────────────┬──────────────────────────────────────────────────┐
                │ 实现                       │ 存储位置与特性                                     │
                ├──────────────────────────┼──────────────────────────────────────────────────┤
                │ InMemoryAgentStateStore   │ ConcurrentHashMap，进程重启即丢                     │
                │                          │ 适合: 测试、演示                                     │
                ├──────────────────────────┼──────────────────────────────────────────────────┤
                │ JsonFileAgentStateStore   │ 本地文件（~/.agentscope/state/）                    │
                │                          │ 按 userId/sessionId 组织目录                         │
                │                          │ 适合: 单机开发、小规模部署                              │
                ├──────────────────────────┼──────────────────────────────────────────────────┤
                │ RedisAgentStateStore      │ Redis 存储（需 agentscope-redis 依赖）               │
                │                          │ 适合: 分布式部署、多实例共享状态                         │
                ├──────────────────────────┼──────────────────────────────────────────────────┤
                │ MysqlAgentStateStore      │ MySQL 存储（需 agentscope-mysql 依赖）               │
                │                          │ 适合: 需要 SQL 审计、与现有数据库集成                    │
                └──────────────────────────┴──────────────────────────────────────────────────┘

                AgentStateStore 的核心方法:

                  save(userId, sessionId, key, state)    — 存一个 State 对象
                  get(userId, sessionId, key, Class<T>) — 读一个 State 对象
                  exists(userId, sessionId)              — 检查 session 是否存在
                  delete(userId, sessionId)              — 删除整个 session
                  listSessionIds(userId)                — 列出某用户所有 session
                """);

        // 演示手动使用 Session API
        demonstrateManualSession();
        System.out.println();
    }

    private static void demonstrateManualSession() {
        System.out.println("  [演示] 手动操作 AgentStateStore API:");
        System.out.println();

        // ── InMemoryAgentStateStore ──
        AgentStateStore memStore = new InMemoryAgentStateStore();

        // 创建一个 AgentState 并存入（RC4 API: store.save(userId, sessionId, key, state)）
        AgentState fresh = AgentState.builder()
                .sessionId("demo-sess-001")
                .build();
        memStore.save("demo-user", "demo-sess-001", "agentState", fresh);

        // 读回
        var loaded = memStore.get("demo-user", "demo-sess-001", "agentState", AgentState.class);
        System.out.println("  InMemoryAgentStateStore:");
        System.out.println("    save 后 exists = " + memStore.exists("demo-user", "demo-sess-001"));
        System.out.println("    get 后 sessionId = " + loaded.map(AgentState::getSessionId).orElse("未找到"));

        // ── JsonFileAgentStateStore ──
        java.nio.file.Path jsonDir = java.nio.file.Paths.get(
                System.getProperty("java.io.tmpdir"), "agentscope-demo-sessions");
        AgentStateStore fileStore = new JsonFileAgentStateStore(jsonDir);

        AgentState fresh2 = AgentState.builder()
                .sessionId("json-sess-001")
                .build();
        fileStore.save("demo-user", "json-sess-001", "agentState", fresh2);

        var loaded2 = fileStore.get("demo-user", "json-sess-001", "agentState", AgentState.class);
        System.out.println("  JsonFileAgentStateStore:");
        System.out.println("    存储目录 = " + jsonDir);
        System.out.println("    save 后 exists = " + fileStore.exists("demo-user", "json-sess-001"));
        System.out.println("    get 后 sessionId = " + loaded2.map(AgentState::getSessionId).orElse("未找到"));

        // 清理
        fileStore.delete("demo-user", "json-sess-001");
        System.out.println("    delete 后 exists = " + fileStore.exists("demo-user", "json-sess-001"));
    }

    // ────────────────────────────────────────────────────────────
    // 阶段 5: 多 call 持久化验证
    // ────────────────────────────────────────────────────────────

    static void stage5_persistenceDemo() {
        System.out.println("━".repeat(60));
        System.out.println("  阶段 5: 多 call 持久化验证");
        System.out.println("━".repeat(60));
        System.out.println();

        ReActAgent agent = ReActAgent.builder()
                .name("state-demo")
                .sysPrompt("""
                        你是一个AI助手。请用中文回答。
                        如果用户告诉你个人信息，请在后续对话中记住并引用。
                        """)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .userId(AgentConfig.DEMO_USER_ID)
                .sessionId("agentstate-caseK-demo")
                .build();

        // ── 第 1 轮 call ──
        System.out.println("  [第 1 轮] 告诉 Agent 个人信息");
        Msg reply1 = agent.call(
                List.of(new UserMessage("我叫小明，今年在学习多智能体框架的开发。")),
                ctx).block();
        System.out.println("  Agent 回复: " + reply1.getTextContent());
        System.out.println();

        // ── 查看 AgentState 内部 ──
        System.out.println("  [查看 AgentState]");
        AgentState state = agent.getAgentState();
        System.out.println("    sessionId = " + state.getSessionId());
        System.out.println("    context 消息数 = " + state.contextMutable().size());
        System.out.println("    curIter = " + state.getCurIter());
        System.out.println("    replyId = " + state.getReplyId());

        System.out.println("    context 消息列表:");
        for (int i = 0; i < state.contextMutable().size(); i++) {
            Msg m = state.contextMutable().get(i);
            System.out.printf("      [%d] role=%-12s name=%-16s text=%s...%n",
                    i, m.getRole(), m.getName(),
                    m.getTextContent().substring(0, Math.min(45, m.getTextContent().length())));
        }
        System.out.println();

        // ── 第 2 轮 call（相同 sessionId）──
        System.out.println("  [第 2 轮] 询问 Agent 刚才告诉它的信息");
        Msg reply2 = agent.call(
                List.of(new UserMessage("我叫什么名字？我刚才说正在学什么？")),
                ctx).block();
        System.out.println("  Agent 回复: " + reply2.getTextContent());
        System.out.println();

        // ── 再次查看 ──
        System.out.println("  [第 2 轮后再次查看]");
        System.out.println("    context 消息数 = " + state.contextMutable().size()
                + " (比第 1 轮多了)");
        System.out.println("    curIter = " + state.getCurIter());
        System.out.println();

        // ── 验证：state 里有上轮的记忆 ──
        System.out.println("  [验证结论]");
        String fullHistory = state.contextMutable().stream()
                .map(m -> m.getRole() + ":" + m.getTextContent())
                .reduce((a, b) -> a + "\n    " + b)
                .orElse("");
        System.out.println("  context 中的完整记忆链:\n    " + fullHistory);
        System.out.println();

        System.out.println(">>> Case K 完成：AgentState 结构、生命周期、Session 后端、持久化验证");
    }
}
