package com.javatoai.agentscope.lesson01;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * Lesson 01 — 最小 {@link ReActAgent}。
 *
 * <p>AgentScope 2.0 的核心是 ReAct 循环：Reason（推理）→ Act（工具）→ 回复。
 * 本课只使用模型 + 系统提示，不启用 workspace / 会话持久化。
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson01.Lesson01BasicReAct
 * }</pre>
 */
public final class Lesson01BasicReAct {

    private static final String AGENT_NAME = "study-assistant";
    private static final String SYSTEM_PROMPT =
            "You are a helpful assistant for learning AgentScope Java 2.0. "
                    + "Reply in the same language as the user.";

    private Lesson01BasicReAct() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 01 · Basic ReActAgent",
                "理解 call() 与 ModelRegistry 字符串模型 ID");
        run();
    }

    /**
     * 构建 Agent、发起一次 call，并打印最终回复。
     */
    public static void run() {
        ReActAgent agent = buildAgent();
        UserMessage question = new UserMessage(
                "用三句话介绍 AgentScope Java 2.0 相比 1.x 最大的变化。");

        Msg reply = agent.call(List.of(question), RuntimeContext.empty()).block();
        System.out.println("[Agent] " + reply.getTextContent());

        System.out.println();
        System.out.println(">>> 下一步: Lesson02HarnessAgent —— 体验 Harness 工程层（会话记忆）");
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
