package com.javatoai.agentscope.advanced.caseI_springboot;

import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 案例 I —— Spring Boot + WebFlux SSE 流式端点。
 *
 * <p>这个案例演示如何在 Spring Boot 环境中暴露 SSE 流式聊天端点。
 * 因为当前项目没有 Spring Boot 依赖，这里用纯 Reactor 模拟了完整的
 * SSE 管线逻辑，可以直接在 main 方法里看到逐 token 输出效果。
 *
 * <p>核心概念:
 * <ul>
 *   <li>Agent 在 Web 容器中的生命周期</li>
 *   <li>{@code Flux<AgentEvent>} 直出 SSE</li>
 *   <li>无状态 Agent Bean 的设计思路</li>
 *   <li>如果接入 Spring Boot，只需加 {@code @GetMapping(produces = TEXT_EVENT_STREAM_VALUE)}</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseI_springboot.CaseISpringBootSse
 * }</pre>
 */
public final class CaseISpringBootSse {

    private CaseISpringBootSse() {}

    // ── 模拟 Spring Boot 的 Mono<ResponseEntity> 包装 ──
    // 在真实 SpringBoot 中是:
    //   @GetMapping(value = "/chat/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    //   public Mono<ResponseEntity<ChatResponse>> chatSync(@RequestParam String message)
    // 这里用纯 Reactor 模拟

    public static void main(String[] args) {
        com.javatoai.agentscope.support.EnvSupport.requireApiKey();
        com.javatoai.agentscope.support.EnvSupport.printBanner(
                "Case I · Spring Boot SSE (模拟)",
                "ReActAgent 作为 Spring Bean → SSE 流式端点");

        System.out.println("""
                在真实的 Spring Boot 项目中，你需要:
                1. 添加依赖: spring-boot-starter-webflux
                2. 把 Agent 声明为 @Bean (单例)
                3. Controller 返回 Flux<AgentEvent>，Spring 自动转 SSE

                本演示跳过 Spring Boot 容器启动，直接用 Reactor 展示等效逻辑。
                """);

        // ── 第 1 部分: 同步端点（相当于 @GetMapping 返回 Mono<Msg>）──
        System.out.println("=".repeat(50));
        System.out.println("  [1] 同步调用端点 (call → Mono<Msg> → JSON Response)");
        System.out.println("=".repeat(50));

        // 等价于 Spring Boot Controller:
        // @GetMapping("/chat/sync")
        // public Mono<ChatResponse> chatSync(@RequestParam String msg) {
        //     return agent.call(List.of(new UserMessage(msg)), context)
        //             .map(reply -> new ChatResponse(reply.getTextContent()));
        // }
        monoEndpointSimulation();

        // ── 第 2 部分: SSE 流式端点（相当于 @GetMapping produces = TEXT_EVENT_STREAM）──
        System.out.println();
        System.out.println("=".repeat(50));
        System.out.println("  [2] SSE 流式端点 (streamEvents → Flux<AgentEvent>)");
        System.out.println("=".repeat(50));

        // 等价于 Spring Boot Controller:
        // @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        // public Flux<AgentEvent> chatStream(@RequestParam String msg) {
        //     return agent.streamEvents(List.of(new UserMessage(msg)), context);
        // }
        sseEndpointSimulation();

        System.out.println();
        System.out.println(">>> Case I 完成：Spring Bean 生命周期、同步/流式端点、无状态设计");
    }

    /** 模拟同步端点：{@code GET /chat/sync?message=xxx} → {@code 200 JSON} */
    private static void monoEndpointSimulation() {
        ReActAgent agent = buildAgent();
        RuntimeContext context = RuntimeContext.builder()
                .userId("api-user")
                .sessionId("sync-session")
                .build();

        System.out.println("[HTTP] GET /chat/sync?message=用三句话介绍微服务架构");
        System.out.print("[Response Body] ");

        Mono<String> responseMono = agent.call(
                List.of(new UserMessage("用三句话介绍微服务架构")),
                context)
                .map(Msg::getTextContent);

        String result = responseMono.block();
        System.out.println("{ \"reply\": \"" + result + "\" }");
    }

    /** 模拟 SSE 流式端点：{@code GET /chat/stream?message=xxx} → {@code text/event-stream} */
    private static void sseEndpointSimulation() {
        ReActAgent agent = buildAgent();
        RuntimeContext context = RuntimeContext.builder()
                .userId("api-user")
                .sessionId("sse-session")
                .build();

        System.out.println("[HTTP] GET /chat/stream?message=用三个要点介绍 Docker 容器技术");
        System.out.print("[SSE Stream] ");

        // 以下是 Spring 自动帮你做的事——把 Flux<AgentEvent> 转成 SSE 格式
        // 真实框架中你只需 return flux，但这里我们模拟 SSE 输出：
        // data: {"type":"TEXT_BLOCK_DELTA","delta":"Doc"}  (Spring 自动序列化)
        agent.streamEvents(
                List.of(new UserMessage("用三个要点介绍 Docker 容器技术")),
                context)
                .doOnNext(event -> {
                    // 模拟 SSE 帧格式——这也是前端能实时渲染的根本原因
                    switch (event.getType()) {
                        case TEXT_BLOCK_DELTA -> {
                            TextBlockDeltaEvent delta = (TextBlockDeltaEvent) event;
                            System.out.print(delta.getDelta());  // 逐字输出
                        }
                        case TOOL_CALL_START -> {
                            System.out.println();
                            System.out.println("data: {\"type\":\"tool_start\",\"name\":\""
                                    + ((ToolCallStartEvent) event).getToolCallName() + "\"}");
                            System.out.print("data: ");
                        }
                        case TOOL_RESULT_END -> {
                            System.out.println();
                            System.out.println("data: {\"type\":\"tool_end\",\"state\":\""
                                    + ((ToolResultEndEvent) event).getState() + "\"}");
                            System.out.print("data: ");
                        }
                    }
                })
                .doOnComplete(() -> {
                    System.out.println();
                    System.out.println("data: {\"type\":\"done\"}");
                })
                .blockLast();
    }

    /** 模拟 Spring Bean 声明：单例 Agent 实例，服务所有请求 */
    private static ReActAgent buildAgent() {
        return ReActAgent.builder()
                .name("spring-api-agent")
                .sysPrompt("你是一个简洁的 API 助手。请用中文回复。答案控制在 100 字以内。")
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }

    // ── 附: Spring Boot 实际集成代码参考（不在此运行）──
    //
    // @Configuration
    // class AgentConfig {
    //     @Bean
    //     public ReActAgent chatAgent() {
    //         return ReActAgent.builder()
    //                 .name("chat-agent")
    //                 .sysPrompt("你是一个有用的助手。")
    //                 .model(ModelSupport.defaultModel())
    //                 .toolkit(new Toolkit())
    //                 .build();
    //     }
    // }
    //
    // @RestController
    // class ChatController {
    //     private final ReActAgent agent;
    //
    //     public ChatController(ReActAgent agent) { this.agent = agent; }
    //
    //     // 同步端点
    //     @GetMapping(value = "/chat/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    //     public Mono<Map<String, String>> chatSync(@RequestParam String msg) {
    //         RuntimeContext ctx = RuntimeContext.builder()
    //                 .userId("web-user").sessionId("web-session").build();
    //         return agent.call(List.of(new UserMessage(msg)), ctx)
    //                 .map(reply -> Map.of("reply", reply.getTextContent()));
    //     }
    //
    //     // SSE 流式端点
    //     @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    //     public Flux<AgentEvent> chatStream(@RequestParam String msg) {
    //         RuntimeContext ctx = RuntimeContext.builder()
    //                 .userId("web-user").sessionId("web-session").build();
    //         return agent.streamEvents(List.of(new UserMessage(msg)), ctx);
    //     }
    // }
}
