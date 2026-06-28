package com.javatoai.agentscope.homework.toolResultConvert;

import com.javatoai.agentscope.support.StreamEventPrinter;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;

/**
 *
 */
public class WeatherAgent {

    public static void main(String[] args) {
        ReActAgent agent = new WeatherAgent().buildAgent();

        String message = "查询所有城市的天气";
        Msg msg = new UserMessage(message);
        RuntimeContext runtimeContext = new RuntimeContext.Builder()
                .userId("wangzhen")
                .sessionId("session-test1")
                .build();
        agent.streamEvents(msg, runtimeContext)
                .doOnNext(StreamEventPrinter::handleEvent)
                .blockLast();
//        Msg block = agent.call(List.of(msg), runtimeContext).block();
//        System.out.println(block.getTextContent());
    }
    @Tool(name = "query_weather", description = "查询天气")
    public Mono<String> queryWeather(@ToolParam(name = "message", description = "查询天气") String query){
        ReActAgent agent = new WeatherAgent().buildAgent();
        Msg msg = new UserMessage(query);
        RuntimeContext runtimeContext = new RuntimeContext.Builder()
                .userId("wangzhen")
                .sessionId("subAgent" + System.currentTimeMillis())
                .build();
        return agent.call(List.of(msg), runtimeContext).map(Msg::getTextContent);
    }

    private Toolkit buildToolkit(){
        Toolkit toolkit = new Toolkit();
//        toolkit.registration().tool().group().apply();
        toolkit.registerTool(new WeatherTool());
        toolkit.createToolGroup("", "");
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
