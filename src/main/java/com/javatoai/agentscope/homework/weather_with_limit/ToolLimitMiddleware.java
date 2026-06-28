package com.javatoai.agentscope.homework.weather_with_limit;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 *
 */
public class ToolLimitMiddleware implements MiddlewareBase {
    private final int limit;
    private int used = 0;
    public ToolLimitMiddleware(int limit){
        this.limit = limit;
    }
    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input, Function<ActingInput, Flux<AgentEvent>> next) {
        if(used + input.toolCalls().size() < limit){
            used += input.toolCalls().size();
            return next.apply(input);
        }else{
            return Flux.just(new RequestStopEvent("本轮工具使用次数已达上限：" + limit));
        }
    }
}
