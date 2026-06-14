package com.javatoai.agentscope.tool;

import com.javatoai.agentscope.support.UserContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * Lesson 07 使用的工具：演示从 {@link io.agentscope.core.agent.RuntimeContext} 注入业务 POJO。
 */
public final class UserTools {

    /**
     * 根据 RuntimeContext 中的 {@link UserContext} 生成个性化问候语。
     *
     * <p>框架会自动把 {@code RuntimeContext.put(UserContext.class, ...)} 注入到该参数，
     * 无需模型在 tool call 中传递 userId / locale。
     *
     * @param displayName 用户显示名
     * @param userContext 运行时上下文（框架注入）
     * @return 个性化问候语
     */
    @Tool(
            name = "personalized_greeting",
            description = "Build a greeting using the current user's locale from runtime context.",
            readOnly = true,
            concurrencySafe = true)
    public String personalizedGreeting(
            @ToolParam(name = "display_name", description = "User display name")
            String displayName,
            UserContext userContext) {
        return switch (userContext.locale()) {
            case "zh-CN" -> "你好，" + displayName + "（用户ID=" + userContext.userId() + "）";
            default -> "Hello, " + displayName + " (userId=" + userContext.userId() + ")";
        };
    }
}
