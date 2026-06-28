package com.javatoai.agentscope.homework.weather_with_limit;

import com.javatoai.agentscope.support.StreamEventPrinter;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 *
 */
public class LimitWeatherAgent {
    public static void main(String[] args) {
        ReActAgent agent = new LimitWeatherAgent().buildAgent();

        String message = "查询所有城市天气";
        Msg msg = new UserMessage(message);
        RuntimeContext runtimeContext = new RuntimeContext.Builder()
                .userId("wangzhen")
                .sessionId("session" + System.currentTimeMillis())
                .build();
        agent.streamEvents(msg, runtimeContext)
                .doOnNext(StreamEventPrinter::handleEvent)
                .blockLast();
    }

    private Toolkit buildToolkit(){
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());
        return toolkit;
    }

    private ReActAgent buildAgent() {
        ReActAgent reActAgent = ReActAgent.builder()
                .sysPrompt("""
                        你是一个天气助手，当用户询问你天气的时候，你需要调用工具获取天气结果，然后给用户说明。
                        """)
                .model(buildModel())
                .toolkit(buildToolkit())
                .stateStore(new JsonFileAgentStateStore(
                        Path.of(System.getProperty("user.home"), ".agentscope", "sessions"))
                )
                .middleware(new ToolLimitMiddleware(3))
                .build();
        return reActAgent;
    }


    private Model buildModel(){
        return OpenAIChatModel.builder()
                .baseUrl("https://open.bigmodel.cn/api/paas/v4/")
                .apiKey(System.getenv("API_KEY_ZAI"))
                .modelName("glm-5")
                .build();
    }
}
