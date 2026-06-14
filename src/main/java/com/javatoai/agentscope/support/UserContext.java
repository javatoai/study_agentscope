package com.javatoai.agentscope.support;

/**
 * Lesson 07 使用的业务上下文 POJO。
 *
 * <p>通过 {@link io.agentscope.core.agent.RuntimeContext#put(Class, Object)} 注入，
 * 框架会自动传递给声明了同类型参数的 {@code @Tool} 方法。
 *
 * @param userId 当前用户 ID
 * @param locale 语言区域，例如 {@code zh-CN}
 */
public record UserContext(String userId, String locale) {
}
