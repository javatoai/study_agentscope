package com.javatoai.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 模型调用耗时中间件：在 onModelCall 层统计 wall-clock 时间。
 */
public final class ModelTimingMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, RuntimeContext rc, ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        long startNanos = System.nanoTime();
        String modelName = input.model().getClass().getSimpleName();
        return next.apply(input)
                .doFinally(signal -> {
                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                    System.out.printf("[timing] %s model=%s took %dms%n",
                            agent.getName(), modelName, elapsedMs);
                });
    }
}
