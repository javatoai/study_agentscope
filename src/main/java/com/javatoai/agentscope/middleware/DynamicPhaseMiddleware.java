package com.javatoai.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * 动态系统提示中间件：把当前场景阶段注入 system prompt，便于多阶段编排。
 */
public final class DynamicPhaseMiddleware implements MiddlewareBase {

    private final Supplier<String> phaseSupplier;

    public DynamicPhaseMiddleware(Supplier<String> phaseSupplier) {
        this.phaseSupplier = phaseSupplier;
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, String currentPrompt) {
        return Mono.just(currentPrompt
                + "\n\n## Current Phase\n"
                + phaseSupplier.get()
                + "\nFollow the phase instructions when choosing tools and reply style.");
    }
}
