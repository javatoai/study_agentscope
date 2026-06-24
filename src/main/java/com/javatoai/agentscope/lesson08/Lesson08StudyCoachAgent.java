package com.javatoai.agentscope.lesson08;

import com.javatoai.agentscope.model.StudySummary;
import com.javatoai.agentscope.support.AgentConfig;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.StreamEventPrinter;
import com.javatoai.agentscope.support.UserContext;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lesson 08 — 综合复杂 Agent 示例（Study Coach）。
 *
 * <p>在单 Agent 实例上组合 2.0 多项能力，模拟接近生产的编排方式：
 * <ul>
 *   <li>{@link HarnessAgent} — workspace、会话持久化、compaction</li>
 *   <li>多工具 — Todo / 时间 / 个性化问候 / 学习笔记</li>
 *   <li>Middleware — 请求追踪、模型耗时、动态 system prompt 阶段注入</li>
 *   <li>{@code enableTaskList} — TaskReminderMiddleware 自动注入任务上下文</li>
 *   <li>{@code streamEvents} — 流式输出最终建议</li>
 *   <li>结构化输出 — {@link StudySummary} 强类型总结</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson08.Lesson08StudyCoachAgent
 * }</pre>
 */
public final class Lesson08StudyCoachAgent {

    private Lesson08StudyCoachAgent() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 08 · Study Coach（复杂 Agent）",
                "Harness + 多工具 + Middleware + 流式 + 结构化输出");
        run();
    }

    public static void run() {
        AtomicReference<String> currentPhase = new AtomicReference<>("phase-1-planning");
        HarnessAgent agent = StudyCoachFactory.build(currentPhase);
        RuntimeContext context = buildRuntimeContext();

        phase1PlanLearning(agent, context, currentPhase);
        phase2ExecuteWithTools(agent, context, currentPhase);
        phase3StreamAdvice(agent, context, currentPhase);
        phase4StructuredSummary(agent, context, currentPhase);

        System.out.println();
        System.out.println(">>> Lesson 08 完成：你已跑通一个接近生产的复杂 Agent 编排。");
    }

    /** 阶段 1：制定学习计划并写入 Todo + 笔记。 */
    private static void phase1PlanLearning(
            HarnessAgent agent, RuntimeContext context, AtomicReference<String> phase) {
        printPhaseHeader("Phase 1", "制定学习计划（todo_write + save_study_note）");
        phase.set("""
                phase-1-planning
                Create a 3-step learning plan for AgentScope Middleware.
                Use todo_write to record tasks.
                Use save_study_note with session_id=%s to store the plan headline.
                """.formatted(StudyCoachFactory.SESSION_ID));

        Msg reply = agent.call(
                new UserMessage("""
                        我要系统学习 AgentScope Java 2.0 的 Middleware 模块。
                        请帮我制定 3 步学习计划，用 todo 工具记录下来，
                        并用 save_study_note 保存一条标题为「Middleware 学习路线」的笔记。
                        """),
                context).block();
        System.out.println("[Reply]\n" + reply.getTextContent());
        System.out.println();
    }

    /** 阶段 2：同 session 下调用多个工具，验证 AgentState 记忆。 */
    private static void phase2ExecuteWithTools(
            HarnessAgent agent, RuntimeContext context, AtomicReference<String> phase) {
        printPhaseHeader("Phase 2", "多工具协作 + 会话记忆（同 sessionId）");
        phase.set("""
                phase-2-execution
                Recall prior plan from conversation history.
                Call get_current_time (Asia/Shanghai), personalized_greeting (display_name=小王),
                then list_study_notes with session_id=%s.
                """.formatted(StudyCoachFactory.SESSION_ID));

        Msg reply = agent.call(
                new UserMessage("""
                        继续刚才的学习计划：
                        1) 查上海当前时间；
                        2) 用 personalized_greeting 问候我（display_name=小王）；
                        3) 列出本 session 已保存的学习笔记；
                        4) 简要说明我们 Phase 1 定了哪几步。
                        """),
                context).block();
        System.out.println("[Reply]\n" + reply.getTextContent());
        System.out.println();
    }

    /** 阶段 3：流式输出学习建议。 */
    private static void phase3StreamAdvice(
            HarnessAgent agent, RuntimeContext context, AtomicReference<String> phase) {
        printPhaseHeader("Phase 3", "streamEvents 流式输出");
        phase.set("""
                phase-3-streaming
                Give concise next-step study advice in Chinese, 3 bullet points max.
                """);

        StreamEventPrinter.printToStdout(
                agent.streamEvents(
                        new UserMessage("根据我们前面的对话，用 3 条要点给出下一步学习建议。"),
                        context),
                "stream");
        System.out.println();
    }

    /** 阶段 4：结构化输出，便于下游程序消费。 */
    private static void phase4StructuredSummary(
            HarnessAgent agent, RuntimeContext context, AtomicReference<String> phase) {
        printPhaseHeader("Phase 4", "结构化输出 StudySummary");
        phase.set("""
                phase-4-structured
                Produce JSON matching StudySummary schema only.
                topic = Middleware learning recap
                difficulty = easy|medium|hard
                keyPoints = 3 strings in Chinese
                """);

        Msg result = agent.call(
                List.of(new UserMessage(
                        "Summarize today's Middleware learning session for programmatic use.")),
                StudySummary.class,
                context).block();

        StudySummary summary = result.getStructuredData(StudySummary.class);
        System.out.println("[Structured Output]");
        System.out.println("  topic      = " + summary.topic());
        System.out.println("  difficulty = " + summary.difficulty());
        System.out.println("  keyPoints  = ");
        summary.keyPoints().forEach(point -> System.out.println("    - " + point));
    }

    private static RuntimeContext buildRuntimeContext() {
        return RuntimeContext.builder()
                .userId(AgentConfig.DEMO_USER_ID)
                .sessionId(StudyCoachFactory.SESSION_ID)
                .put("request_id", "coach-req-001")
                .put(UserContext.class, new UserContext(AgentConfig.DEMO_USER_ID, "zh-CN"))
                .build();
    }

    private static void printPhaseHeader(String phase, String description) {
        System.out.println("-".repeat(60));
        System.out.println("  " + phase + " · " + description);
        System.out.println("-".repeat(60));
    }
}
