package com.javatoai.agentscope.lesson06;

import com.javatoai.agentscope.model.StudySummary;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * Lesson 06 — 结构化输出（Structured Output）。
 *
 * <p>当业务代码需要<strong>程序化处理</strong> Agent 回复时（分类、表单填充、数据抽取），
 * 可传入 Java record / class 作为 Schema，框架会强制模型按 JSON Schema 返回。
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson06.Lesson06StructuredOutput
 * }</pre>
 */
public final class Lesson06StructuredOutput {

    private static final String AGENT_NAME = "structured-demo";
    private static final String SYSTEM_PROMPT =
            "You are a technical tutor. Summarize learning topics clearly.";

    private Lesson06StructuredOutput() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 06 · Structured Output",
                "call(..., StudySummary.class) 获取强类型结果");
        run();
    }

    public static void run() {
        ReActAgent agent = buildAgent();
        UserMessage question = new UserMessage(
                "Summarize AgentScope Java 2.0 Harness architecture for a beginner.");

        Msg result = agent.call(List.of(question), StudySummary.class).block();
        StudySummary summary = result.getStructuredData(StudySummary.class);

        System.out.println("[Structured Output]");
        System.out.println("  topic      = " + summary.topic());
        System.out.println("  difficulty = " + summary.difficulty());
        System.out.println("  keyPoints  = ");
        summary.keyPoints().forEach(point -> System.out.println("    - " + point));

        System.out.println();
        System.out.println(">>> 下一步: Lesson07RuntimeContext —— 工具注入业务上下文");
    }

    private static ReActAgent buildAgent() {
        return ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }
}
