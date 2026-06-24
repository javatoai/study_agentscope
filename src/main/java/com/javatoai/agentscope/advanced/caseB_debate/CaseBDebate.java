package com.javatoai.agentscope.advanced.caseB_debate;

import com.javatoai.agentscope.model.StudySummary;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * 案例 B —— Agent 辩论。
 *
 * <p>两个 Agent 对同一问题给出相反观点，第三个 Judge Agent 评分并综合。
 *
 * <p>核心概念:
 * <ul>
 *   <li>同一问题不同角色 Prompt</li>
 *   <li>多 Agent 并发获取不同观点</li>
 *   <li>结构化输出做评判</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseB_debate.CaseBDebate
 * }</pre>
 */
public final class CaseBDebate {

    // 正方：支持静态类型
    private static final String PRO_PROMPT = """
            你是一个辩手，立场是【支持静态类型语言】（如 Java、TypeScript、Rust）。
            你的任务：论证为什么静态类型在大规模软件项目中更优越。
            请围绕以下几点展开：类型安全、重构信心、IDE 支持、提前发现 bug。
            请用中文回复，最多 3-4 个要点。
            """;

    // 反方：支持动态类型
    private static final String CON_PROMPT = """
            你是一个辩手，立场是【支持动态类型语言】（如 Python、JavaScript）。
            你的任务：论证为什么动态类型在快速开发和原型验证中更优越。
            请围绕以下几点展开：开发速度、灵活性、更少模板代码、REPL 驱动开发。
            请用中文回复，最多 3-4 个要点。
            """;

    // 法官
    private static final String JUDGE_PROMPT = """
            你是一个公正的裁判，正在评估两个 AI 辩手的辩论。
            请公平评价双方论点，然后输出结构化的评判结果。
            请用中文回复。
            """;

    private CaseBDebate() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case B · Agent Debate",
                "正方 vs 反方 → Judge 评分并综合");

        ReActAgent proAgent = buildDebater("pro-static", PRO_PROMPT);
        ReActAgent conAgent = buildDebater("con-dynamic", CON_PROMPT);
        ReActAgent judge = buildJudge();

        String topic = "大型软件项目中应该优先选用静态类型语言还是动态类型语言？";

        // Phase 1: 双方辩论
        System.out.println("=".repeat(50));
        System.out.println("  辩题: " + topic);
        System.out.println("=".repeat(50));

        Msg proReplay = proAgent.call(
                List.of(new UserMessage("请为「%s」的正方（静态类型）立场辩护。".formatted(topic))),
                RuntimeContext.empty()).block();

        Msg conReply = conAgent.call(
                List.of(new UserMessage("请为「%s」的反方（动态类型）立场辩护。".formatted(topic))),
                RuntimeContext.empty()).block();

        System.out.println("[正方 · 静态类型派]");
        System.out.println(proReplay.getTextContent());
        System.out.println();
        System.out.println("[反方 · 动态类型派]");
        System.out.println(conReply.getTextContent());
        System.out.println();

        // Phase 2: Judge 评判（结构化输出）
        System.out.println("-".repeat(50));
        System.out.println("  Judge 评判");
        System.out.println("-".repeat(50));

        Msg verdict = judge.call(
                List.of(new UserMessage("""
                        以下是一场辩论：

                        正方（支持静态类型）的观点：
                        %s

                        反方（支持动态类型）的观点：
                        %s

                        请对双方进行评分（1-10），并给出综合结论。
                        """.formatted(
                        proReplay.getTextContent(),
                        conReply.getTextContent()))),
                StudySummary.class,
                RuntimeContext.empty()).block();

        StudySummary summary = verdict.getStructuredData(StudySummary.class);
        System.out.println("[评判结果]");
        System.out.println("  topic      = " + summary.topic());
        System.out.println("  difficulty = " + summary.difficulty());
        summary.keyPoints().forEach(p -> System.out.println("    - " + p));

        System.out.println();
        System.out.println(">>> Case B 完成：辩论、多方观点、结构化评判");
    }

    private static ReActAgent buildDebater(String name, String prompt) {
        return ReActAgent.builder()
                .name(name)
                .sysPrompt(prompt)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }

    private static ReActAgent buildJudge() {
        return ReActAgent.builder()
                .name("judge")
                .sysPrompt(JUDGE_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }
}
