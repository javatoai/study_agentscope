package com.javatoai.agentscope.lesson02;

import com.javatoai.agentscope.support.AgentConfig;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;

/**
 * Lesson 02 — {@link HarnessAgent} 入门。
 *
 * <p>2.0 推荐的生产入口是 HarnessAgent，在 ReAct 核心之上叠加：
 * <ul>
 *   <li>Workspace 驱动的人设（{@code AGENTS.md}）</li>
 *   <li>按 sessionId 自动持久化对话状态</li>
 *   <li>超长对话自动 compaction + 长期记忆写入 MEMORY.md</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson02.Lesson02HarnessAgent
 * }</pre>
 */
public final class Lesson02HarnessAgent {

    private static final String AGENT_NAME = "note-taker";
    private static final String SYSTEM_PROMPT = "You are a note-taking assistant.";

    /** 消息数超过 30 条时触发压缩，保留最近 10 条。 */
    private static final int COMPACTION_TRIGGER = 30;
    private static final int COMPACTION_KEEP = 10;

    private Lesson02HarnessAgent() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 02 · HarnessAgent",
                "同一 sessionId 下，第二轮对话能记住第一轮内容");
        run();
    }

    public static void run() {
        HarnessAgent agent = buildAgent();
        RuntimeContext context = buildRuntimeContext();

        Msg turnOne = agent.call(
                new UserMessage("我叫小王，今天在学习 AgentScope Java 2.0 的 Harness 模块。"),
                context).block();
        System.out.println("[Turn 1] " + turnOne.getTextContent());
        System.out.println();

        // 相同 sessionId：框架自动从 AgentStateStore 恢复上下文
        Msg turnTwo = agent.call(
                new UserMessage("我叫什么？今天在学习什么？"),
                context).block();
        System.out.println("[Turn 2] " + turnTwo.getTextContent());

        System.out.println();
        System.out.printf(
                "提示: 状态保存在 ~/.agentscope/state/%s/%s/%s/%n",
                AGENT_NAME, AgentConfig.DEMO_USER_ID, AgentConfig.DEMO_SESSION_ID);
        System.out.println(">>> 下一步: Lesson03Streaming —— 体验 streamEvents 流式输出");
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

    private static RuntimeContext buildRuntimeContext() {
        return RuntimeContext.builder()
                .sessionId(AgentConfig.DEMO_SESSION_ID)
                .userId(AgentConfig.DEMO_USER_ID)
                .build();
    }
}
