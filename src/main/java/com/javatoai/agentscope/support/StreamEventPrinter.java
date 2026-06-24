package com.javatoai.agentscope.support;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import reactor.core.publisher.Flux;

/**
 * 将 {@code streamEvents} 输出格式化为适合终端阅读的流。
 */
public final class StreamEventPrinter {

    private StreamEventPrinter() {
    }

    public static void printToStdout(Flux<AgentEvent> events, String prefix) {
        System.out.print("[" + prefix + "] ");
        events.doOnNext(StreamEventPrinter::handleEvent).blockLast();
        System.out.println();
    }

    private static void handleEvent(AgentEvent event) {
        switch (event.getType()) {
            case TEXT_BLOCK_DELTA ->
                    System.out.print(((TextBlockDeltaEvent) event).getDelta());
            case TOOL_CALL_START ->
                    System.out.printf("%n  ↳ tool: %s%n  ",
                            ((ToolCallStartEvent) event).getToolCallName());
            case TOOL_RESULT_END -> {
                ToolResultEndEvent end = (ToolResultEndEvent) event;
                System.out.printf("%n  ↳ tool done (%s)%n  ", end.getState());
            }
            default -> {
                // thinking / lifecycle events intentionally omitted for brevity
            }
        }
    }
}
