package com.javatoai.agentscope.lesson07;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.support.UserContext;
import com.javatoai.agentscope.tool.UserTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * Lesson 07 — {@link RuntimeContext} 与工具上下文注入。
 *
 * <p>{@code RuntimeContext} 是<strong>单次 call 级别</strong>的元数据容器：
 * <ul>
 *   <li>{@code userId / sessionId} — 驱动 AgentState 持久化</li>
 *   <li>{@code put(Class, value)} — 注入业务 POJO，自动传给 {@code @Tool} 方法参数</li>
 *   <li>{@code put(String, value)} — 自由字符串键值，供 Middleware 等读取</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.lesson07.Lesson07RuntimeContext
 * }</pre>
 */
public final class Lesson07RuntimeContext {

    private static final String AGENT_NAME = "context-demo";
    private static final String SYSTEM_PROMPT =
            "You are a helpful assistant. When greeting the user, "
                    + "call personalized_greeting with display_name 小王.";

    private Lesson07RuntimeContext() {
    }

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner(
                "Lesson 07 · RuntimeContext",
                "put(UserContext.class, ...) 自动注入到 @Tool 方法");
        run();
    }

    public static void run() {
        ReActAgent agent = buildAgent();

        RuntimeContext context = RuntimeContext.builder()
                .userId("user-1001")
                .sessionId("ctx-demo-session")
                .put("request_id", "req-20250610-001")
                .put(UserContext.class, new UserContext("user-1001", "zh-CN"))
                .build();

        UserMessage question = new UserMessage("请用工具给我一句个性化问候。");
        Msg reply = agent.call(List.of(question), context).block();

        System.out.println("[request_id] " + context.get("request_id"));
        System.out.println("[Agent] " + reply.getTextContent());

        System.out.println();
        System.out.println(">>> 入门七课完成！阅读 README 继续 Harness / MCP / 生产部署。");
    }

    private static ReActAgent buildAgent() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new UserTools());

        return ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .toolkit(toolkit)
                .build();
    }
}
