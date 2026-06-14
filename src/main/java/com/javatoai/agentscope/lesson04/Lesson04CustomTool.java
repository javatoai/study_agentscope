package com.javatoai.agentscope.lesson04;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.tool.TimeTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * Lesson 04 — 自定义 {@code @Tool} 工具。
 *
 * <p>Agent 通过 {@link Toolkit} 注册 Java 方法，模型在 ReAct 循环中自动选择并调用。
 * 本例注册 {@link TimeTools#getCurrentTime(String)}，演示 Reason → Tool → Reply 完整闭环。
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson04.Lesson04CustomTool
 * }</pre>
 */
public final class Lesson04CustomTool {

    private static final String AGENT_NAME = "tool-demo";
    private static final String SYSTEM_PROMPT =
            "You are a helpful assistant. When asked about time, "
                    + "use the get_current_time tool with timezone Asia/Shanghai.";

    private Lesson04CustomTool() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 04 · Custom Tool",
                "注册 @Tool 方法，观察 Agent 的 Reason → Tool → Reply 流程");
        run();
    }

    public static void run() {
        ReActAgent agent = buildAgent();
        UserMessage question = new UserMessage("现在上海几点了？请调用工具后再回答。");

        Msg reply = agent.call(List.of(question), RuntimeContext.empty()).block();
        System.out.println("[Agent] " + reply.getTextContent());

        System.out.println();
        System.out.println(">>> 下一步: Lesson05MultiUserSession —— 单 Agent 实例服务多用户");
    }

    private static ReActAgent buildAgent() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new TimeTools());

        return ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(toolkit)
                .build();
    }
}
