package com.javatoai.agentscope.lesson05;

import com.javatoai.agentscope.support.AgentConfig;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;

/**
 * Lesson 05 — 单 Agent 实例、多用户 / 多会话隔离。
 *
 * <p>Agent 在两次 call 之间是<strong>无状态</strong>的：
 * 同一个 {@link HarnessAgent} 实例可以并发服务多个用户，只需在每次 call 时传入不同的
 * {@code (userId, sessionId)}，框架会自动加载 / 保存对应的 AgentState。
 *
 * <p>这是 HTTP 服务场景的常见模式：启动时创建一个 Agent Bean，每个请求构造独立的
 * {@link RuntimeContext}。
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson05.Lesson05MultiUserSession
 * }</pre>
 */
public final class Lesson05MultiUserSession {

    private static final String AGENT_NAME = "multi-user-assistant";
    private static final String SYSTEM_PROMPT =
            "You are a helpful assistant. Remember facts the user tells you in this session.";

    private static final int COMPACTION_TRIGGER = 30;
    private static final int COMPACTION_KEEP = 10;

    private Lesson05MultiUserSession() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 05 · Multi-User Session",
                "一个 HarnessAgent 实例，alice 与 bob 的会话互不干扰");
        run();
    }

    public static void run() {
        HarnessAgent agent = buildAgent();

        RuntimeContext aliceContext = RuntimeContext.builder()
                .userId("alice")
                .sessionId("session-001")
                .build();
        RuntimeContext bobContext = RuntimeContext.builder()
                .userId("bob")
                .sessionId("session-001")
                .build();

        // Alice 第一轮：写入个人信息
        agent.call(
                new UserMessage("My name is Alice and I study distributed systems."),
                aliceContext).block();

        // Bob 第一轮：写入不同信息
        agent.call(
                new UserMessage("My name is Bob and I study database indexing."),
                bobContext).block();

        // 第二轮：分别询问，验证 (userId, sessionId) 隔离
        Msg aliceReply = agent.call(
                new UserMessage("What is my name and what do I study?"),
                aliceContext).block();
        Msg bobReply = agent.call(
                new UserMessage("What is my name and what do I study?"),
                bobContext).block();

        System.out.println("[Alice] " + aliceReply.getTextContent());
        System.out.println("[Bob]   " + bobReply.getTextContent());

        System.out.println();
        System.out.printf(
                "状态目录: ~/.agentscope/state/%s/{userId}/{sessionId}/%n",
                AGENT_NAME);
        System.out.println(">>> 下一步: Lesson06StructuredOutput —— 结构化 JSON 输出");
    }

    private static HarnessAgent buildAgent() {
        return HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(COMPACTION_TRIGGER)
                        .keepMessages(COMPACTION_KEEP)
                        .build())
                .build();
    }
}
