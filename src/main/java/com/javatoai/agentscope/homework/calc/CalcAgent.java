package com.javatoai.agentscope.homework.calc;

import com.javatoai.agentscope.support.StreamEventPrinter;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;

import java.nio.file.Path;

/**
 *
 */
public class CalcAgent {
    public static void main(String[] args) {
        UserMessage userMessage = new UserMessage("帮我计算表达式：(4+4)*2/5+1-(3*5)");
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId("wangzhen")
                .sessionId("session2")
                .build();
        ReActAgent reActAgent = new CalcAgent().buildAgent();
        reActAgent.streamEvents(userMessage, runtimeContext)
                .doOnNext(StreamEventPrinter::handleEvent)
                .blockLast();


    }

    private Toolkit buildToolkit(){
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new CalcTool());
        return toolkit;
    }


    private ReActAgent buildAgent(){
        return ReActAgent.builder()
                .sysPrompt("""
                        你是计算器助手，必须调用 calc 工具计算结果，禁止自己计算。
                        每次只算两个数的 + - * / 一种运算，多步运算拆成多次工具调用。
                        运算顺序：先括号、再乘除、最后加减。
                        """)
                .model(buildModel())
                .toolkit(buildToolkit())
                .stateStore(new JsonFileAgentStateStore(Path.of(System.getProperty("user.home"), ".agentscope", "sessions")))
                .build();
    }

    private OpenAIChatModel buildModel(){
        return OpenAIChatModel.builder()
                .baseUrl("https://open.bigmodel.cn/api/paas/v4/")
                .modelName("glm-5")
                .apiKey(System.getenv("API_KEY_ZAI"))
                .build();
    }
}
