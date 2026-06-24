package com.javatoai.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 请求追踪中间件：记录 Agent 生命周期与工具调用（RC1 兼容，不依赖 getRuntimeContext）。
 */
public final class RequestTraceMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        int messageCount = input.msgs() == null ? 0 : input.msgs().size();
        System.out.printf("[trace] agent=%s incomingMessages=%d%n", agent.getName(), messageCount);
        return next.apply(input)
                .doOnComplete(() -> System.out.println("[trace] reply finished for " + agent.getName()));
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent, ActingInput input, Function<ActingInput, Flux<AgentEvent>> next) {
        String toolNames = input.toolCalls().stream()
                .map(ToolUseBlock::getName)
                .collect(Collectors.joining(", "));
        System.out.println("[trace] acting tools: " + toolNames);
        return next.apply(input);
    }
}
