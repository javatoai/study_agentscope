package com.javatoai.agentscope.lesson03;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

/**
 * Lesson 03 — 流式事件 {@code streamEvents}。
 *
 * <p>2.0 将推理过程拆成类型化事件流，适合 Web / TUI 实时渲染：
 * 文本增量、工具调用开始、思考块、回复结束等。
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson03.Lesson03Streaming
 * }</pre>
 */
public final class Lesson03Streaming {

    private static final String AGENT_NAME = "stream-demo";
    private static final String SYSTEM_PROMPT =
            "You are a concise assistant. Reply in Chinese.";

    private Lesson03Streaming() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 03 · Streaming Events",
                "用 streamEvents() 逐 token 打印模型输出");
        run();
    }

    public static void run() {
        ReActAgent agent = buildAgent();
        UserMessage question = new UserMessage("用三个要点介绍 ReAct 推理循环。");

        System.out.print("[Streaming] ");
        agent.streamEvents(question)
                .doOnNext(Lesson03Streaming::handleEvent)
                .blockLast();

        System.out.println();
        System.out.println();
        System.out.println(">>> 下一步: Lesson04CustomTool —— 让 Agent 调用自定义 Java 工具");
    }

    private static ReActAgent buildAgent() {
        return ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }

    /**
     * 按事件类型分发处理逻辑。
     */
    private static void handleEvent(AgentEvent event) {
        AgentEventType type = event.getType();
        if (type == AgentEventType.TEXT_BLOCK_DELTA) {
            TextBlockDeltaEvent deltaEvent = (TextBlockDeltaEvent) event;
            System.out.print(deltaEvent.getDelta());
            return;
        }
        if (type == AgentEventType.TOOL_CALL_START) {
            ToolCallStartEvent toolEvent = (ToolCallStartEvent) event;
            // RC1 使用 getToolCallName()；官方文档部分示例为 getToolName()
            System.out.println("\n[tool] " + toolEvent.getToolCallName());
        }
    }
}
