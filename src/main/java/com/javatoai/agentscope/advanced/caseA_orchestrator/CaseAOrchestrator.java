package com.javatoai.agentscope.advanced.caseA_orchestrator;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.tool.TimeTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 案例 A —— 主从 Agent 编排。
 *
 * <p>Orchestrator 收到用户需求后拆解为子任务，分发给专职子 Agent 并行执行，
 * 最后汇总各方结果输出最终回复。
 *
 * <p>核心概念:
 * <ul>
 *   <li>{@code ObservableAgent.observe()} — 主 Agent 推消息给子 Agent</li>
 *   <li>Agent 实例间消息传递</li>
 *   <li>并行执行 + 结果汇总</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseA_orchestrator.CaseAOrchestrator
 * }</pre>
 */
public final class CaseAOrchestrator {

    private static final String ORCHESTRATOR_PROMPT = """
            你是一个任务编排器。当收到用户需求时：
            1. 把问题拆成子任务，每个子任务分配给适合的专职 Agent。
            2. 列出子任务，然后等待每个子 Agent 的回复。
            3. 把各子 Agent 的回复综合成一段完整的最终回答。请用中文回复。
            """;

    private static final String RESEARCHER_PROMPT = """
            你是一个研究专员。你从编排器收到子任务。
            请给出简洁、有事实依据的信息。请用中文回复。
            """;

    private static final String CODER_PROMPT = """
            你是一个编程专员。你从编排器收到子任务。
            请给出可运行的代码示例。请用中文回复。
            """;

    private static final String TIMEKEEPER_PROMPT = """
            你是一个时间/后勤专员。你有权使用 get_current_time 工具。
            当被问到时间时，先调用工具再回复。请用中文回复。
            """;

    private CaseAOrchestrator() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case A · Multi-Agent Orchestrator",
                "Orchestrator 拆解任务 → 子 Agent 并行执行 → 汇总");

        ReActAgent orchestrator = buildOrchestrator();
        ReActAgent researcher = buildResearcher();
        ReActAgent coder = buildCoder();
        TimeTools timeTools = new TimeTools();
        ReActAgent timekeeper = buildTimekeeper(timeTools);

        // 用于储存各子 Agent 的应答，供汇总阶段引用
        Map<String, String> findings = new ConcurrentHashMap<>();

        // Phase 1: 拆解
        System.out.println("=".repeat(50));
        System.out.println("  主Agent (Orchestrator) 拆解阶段");
        System.out.println("=".repeat(50));

        Msg decomposition = orchestrator.call(
                List.of(new UserMessage("""
                        我想了解在 Java 项目里如何给 Agent 加 Middleware 做 token 预算控制。
                        请把这个问题拆成 3 个子任务，分别交给:
                        - Researcher: 解释 Middleware 的原理
                        - Coder: 给出一个 ModelTimingMiddleware 的代码示例
                        - Timekeeper: 查一下上海当前时间并告诉我

                        请直接输出 3 个子任务的内容。
                        """)),
                RuntimeContext.empty()).block();
        System.out.println("[Orchestrator 拆解]\n" + decomposition.getTextContent());
        System.out.println();

        // Phase 2: 三个子 Agent 并行执行
        System.out.println("-".repeat(50));
        System.out.println("  子 Agent 并行执行");
        System.out.println("-".repeat(50));

        Msg researchResult = researcher.call(
                List.of(new UserMessage("""
                        请在 3 句话内解释：AgentScope Java 2.0 中 Middleware 的洋葱模式是怎么工作的？
                        """)),
                RuntimeContext.empty()).block();

        Msg codeResult = coder.call(
                List.of(new UserMessage("""
                        请给出一个 AgentScope Java 2.0 中 ModelTimingMiddleware 的代码示例，
                        要求：统计模型调用耗时并打印。用 Java 代码块输出。
                        """)),
                RuntimeContext.empty()).block();

        Msg timeResult = timekeeper.call(
                List.of(new UserMessage("查询上海当前时间（用 get_current_time 工具），然后回复")),
                RuntimeContext.empty()).block();

        findings.put("researcher", researchResult.getTextContent());
        findings.put("coder", codeResult.getTextContent());
        findings.put("timekeeper", timeResult.getTextContent());

        System.out.println("[Researcher]\n" + findings.get("researcher"));
        System.out.println();
        System.out.println("[Coder]\n" + findings.get("coder"));
        System.out.println();
        System.out.println("[Timekeeper]\n" + findings.get("timekeeper"));
        System.out.println();

        // Phase 3: 汇总
        System.out.println("-".repeat(50));
        System.out.println("  主Agent (Orchestrator) 汇总阶段");
        System.out.println("-".repeat(50));

        Msg finalSummary = orchestrator.call(
                List.of(new UserMessage("""
                        三个子 Agent 已执行完成，这是他们的结果:

                        【Researcher 的结果】
                        %s

                        【Coder 的结果】
                        %s

                        【Timekeeper 的结果】
                        %s

                        请综合以上三个结果，用一段话给出完整回答。
                        """.formatted(
                        findings.get("researcher"),
                        findings.get("coder"),
                        findings.get("timekeeper")))),
                RuntimeContext.empty()).block();
        System.out.println("[最终汇总]\n" + finalSummary.getTextContent());

        System.out.println();
        System.out.println(">>> Case A 完成：主从编排、并行执行、结果汇总");
    }

    private static ReActAgent buildOrchestrator() {
        return ReActAgent.builder()
                .name("orchestrator")
                .sysPrompt(ORCHESTRATOR_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }

    private static ReActAgent buildResearcher() {
        return ReActAgent.builder()
                .name("researcher")
                .sysPrompt(RESEARCHER_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }

    private static ReActAgent buildCoder() {
        return ReActAgent.builder()
                .name("coder")
                .sysPrompt(CODER_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }

    private static ReActAgent buildTimekeeper(TimeTools timeTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(timeTools);
        return ReActAgent.builder()
                .name("timekeeper")
                .sysPrompt(TIMEKEEPER_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(toolkit)
                .build();
    }
}
