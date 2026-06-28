package com.javatoai.agentscope.homework.dispatcher;

import com.javatoai.agentscope.homework.weather.WeatherAgent;
import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.support.StreamEventPrinter;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;

/**
 *
 */
public class DispatcherAgent {
    public static void main(String[] args) {
        Model model = ModelSupport.defaultModel();
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherAgent());

        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId("wangzhen")
                .sessionId("agent_" + System.currentTimeMillis())
                .build();

        ReActAgent reactAgent = ReActAgent.builder()
                .model(model)
                .name("dispatcher_agent")
                .sysPrompt("""
                        你是一个任务分发员，分给你的任务，你需要调用不同的工具去执行，然后汇总结果。
                        """)
                .toolkit(toolkit)
                .build();

        UserMessage userMessage = new UserMessage("我想去北京玩，帮我查询北京的天气");

        reactAgent.streamEvents(userMessage, runtimeContext)
                .doOnNext(StreamEventPrinter::handleEvent)
                .blockLast();

    }

}
