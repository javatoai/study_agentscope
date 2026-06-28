package com.javatoai.agentscope.homework.todo;

import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.support.StreamEventPrinter;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Scanner;

/**
 *
 */
public class TodoAgent {
    public static void main(String[] args) {
        UserMessage userMessage = new UserMessage("我有一些任务需要完成，包括买菜，做饭，吃饭，洗完，请你帮我整理这些任务，当我完成了某些任务的时候，我会告诉你");
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId("wangzhen")
                .sessionId("session3")
                .build();

        ReActAgent reActAgent = new TodoAgent().buildAgent();
        reActAgent.streamEvents(userMessage, runtimeContext)
                .doOnNext(StreamEventPrinter::handleEvent)
                .blockLast();
        while (true){
            Scanner scanner = new Scanner(System.in);
            String s = scanner.nextLine();
            if(Objects.equals(s, "exit")){
                return;
            }else{
                reActAgent.streamEvents(new UserMessage(s), runtimeContext)
                        .doOnNext(StreamEventPrinter::handleEvent)
                        .blockLast();
            }
        }


    }


    private ReActAgent buildAgent(){
        return ReActAgent.builder()
                .name("任务完成agent")
                .sysPrompt("""
                        调用目前的工具，帮助用户完成任务统计和规划。
                        """)
                .model(ModelSupport.defaultModel())
                .toolkit(buildTool())
                .stateStore(new JsonFileAgentStateStore(Path.of(System.getProperty("user.home"), ".agentscope", "sessions")))
                .build();
    }

    private Toolkit buildTool(){
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new TodoTool());
        return toolkit;
    }
}



