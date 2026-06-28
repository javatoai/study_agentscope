package com.javatoai.agentscope.support;

import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.DataBlockDeltaEvent;
import io.agentscope.core.event.DataBlockEndEvent;
import io.agentscope.core.event.DataBlockStartEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ExternalExecutionResultEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.message.ToolResultState;
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

    public static void handleEvent(AgentEvent event) {
        switch (event.getType()) {
            // ── 正文文本块 (streaming) ──
            case TEXT_BLOCK_START -> {
            } // 静默
            case TEXT_BLOCK_DELTA ->
                    System.out.print(((TextBlockDeltaEvent) event).getDelta());
            case TEXT_BLOCK_END -> {
            } // 静默

            // ── 思考块 (流式) ──
            case THINKING_BLOCK_START ->
                    System.out.print("\n  🤔 ");
            case THINKING_BLOCK_DELTA ->
                    System.out.print(((ThinkingBlockDeltaEvent) event).getDelta());
            case THINKING_BLOCK_END ->
                    System.out.println();

            // ── 工具调用 (开始→参数增量→结束) ──
            case TOOL_CALL_START -> {
                ToolCallStartEvent e = (ToolCallStartEvent) event;
                System.out.printf("%n  ↳ 调用工具: %s%n", e.getToolCallName());
            }
            case TOOL_CALL_DELTA -> {
                ToolCallDeltaEvent e = (ToolCallDeltaEvent) event;
                if (e.getDelta() != null && !e.getDelta().isEmpty()) {
                    System.out.print("    " + e.getDelta());
                }
            }
            case TOOL_CALL_END ->
                    System.out.println();

            // ── 工具结果 ──
            case TOOL_RESULT_START -> {
                ToolResultStartEvent e = (ToolResultStartEvent) event;
                System.out.printf("    ⏳ 执行中: %s%n", e.getToolCallName());
            }
            case TOOL_RESULT_TEXT_DELTA -> {
                String delta = ((ToolResultTextDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    System.out.print("    " + delta);
                }
            }
            case TOOL_RESULT_DATA_DELTA -> {
                ToolResultDataDeltaEvent e = (ToolResultDataDeltaEvent) event;
                System.out.printf("    [数据] %s%n", e.getData());
            }
            case TOOL_RESULT_END -> {
                ToolResultEndEvent e = (ToolResultEndEvent) event;
                String icon = iconForState(e.getState());
                System.out.printf("    %s %s%n", icon, e.getState());
            }

            // ── 数据块 (结构化输出) ──
            case DATA_BLOCK_START ->
                    System.out.print("\n  [数据] ");
            case DATA_BLOCK_DELTA ->
                    System.out.print(((DataBlockDeltaEvent) event).getDelta());
            case DATA_BLOCK_END ->
                    System.out.println();

            // ── Agent 生命周期 ──
            case AGENT_START -> {
                AgentStartEvent e = (AgentStartEvent) event;
                System.out.printf("%n  ▶ Agent 启动: %s (session: %s)%n", e.getName(), ellipsis(e.getSessionId(), 12));
            }
            case AGENT_END -> {
                AgentEndEvent e = (AgentEndEvent) event;
                System.out.printf("  ◼ Agent 结束 (reply: %s)%n", ellipsis(e.getReplyId(), 8));
            }
            case AGENT_RESULT -> {
                AgentResultEvent e = (AgentResultEvent) event;
                System.out.printf("%n  ★ 最终结果: role=%s, content长度=%d%n",
                        e.getResult().getRole(),
                        e.getResult().getTextContent() != null ? e.getResult().getTextContent().length() : 0);
            }

            // ── 模型调用 ──
            case MODEL_CALL_START ->
                    System.out.print("\n  [模型调用] 开始…");
            case MODEL_CALL_END -> {
                ModelCallEndEvent e = (ModelCallEndEvent) event;
                ChatUsage u = e.getUsage();
                if (u != null) {
                    System.out.printf(" 结束 (in=%d out=%d total=%d, %.1fs)%n",
                            u.getInputTokens(), u.getOutputTokens(), u.getTotalTokens(), u.getTime());
                } else {
                    System.out.println(" 结束");
                }
            }

            // ── 权限/审批 ──
            case REQUIRE_USER_CONFIRM -> {
                RequireUserConfirmEvent e = (RequireUserConfirmEvent) event;
                String names = e.getToolCalls().stream()
                        .map(tc -> tc.getName())
                        .reduce((a, b) -> a + ", " + b).orElse("?");
                System.out.printf("%n  ⚠ 需要用户确认: %s (共 %d 个工具)%n",
                        names, e.getToolCalls().size());
            }
            case REQUIRE_EXTERNAL_EXECUTION -> {
                RequireExternalExecutionEvent e = (RequireExternalExecutionEvent) event;
                String names = e.getToolCalls().stream()
                        .map(tc -> tc.getName())
                        .reduce((a, b) -> a + ", " + b).orElse("?");
                System.out.printf("%n  ⚡ 需要外部执行: %s (共 %d 个工具)%n",
                        names, e.getToolCalls().size());
            }
            case USER_CONFIRM_RESULT -> {
                UserConfirmResultEvent e = (UserConfirmResultEvent) event;
                long confirmed = e.getConfirmResults().stream()
                        .filter(r -> r.isConfirmed()).count();
                System.out.printf("  ✓ 用户确认: %d/%d 通过%n",
                        confirmed, e.getConfirmResults().size());
            }
            case EXTERNAL_EXECUTION_RESULT -> {
                ExternalExecutionResultEvent e = (ExternalExecutionResultEvent) event;
                System.out.printf("  ✓ 外部执行完成: %d 个结果%n", e.getToolResults().size());
            }

            // ── 异常/边界 ──
            case EXCEED_MAX_ITERS -> {
                ExceedMaxItersEvent e = (ExceedMaxItersEvent) event;
                System.out.printf("%n  ⚠ 超过最大迭代次数 (当前 %d / 上限 %d)%n",
                        e.getCurrentIter(), e.getMaxIters());
            }
            case REQUEST_STOP -> {
                RequestStopEvent e = (RequestStopEvent) event;
                System.out.printf("%n  ⛔ 请求停止: reason=%s%n", e.getGenerateReason());
            }
            case SUBAGENT_EXPOSED -> {
                SubagentExposedEvent e = (SubagentExposedEvent) event;
                System.out.printf("%n  👥 子Agent 暴露: %s (label=%s)%n",
                        e.getSubagentId(), e.getLabel());
            }
            case HINT_BLOCK -> {
                HintBlockEvent e = (HintBlockEvent) event;
                System.out.printf("%n  💡 提示 [%s]: %s%n",
                        e.getHintSource(), ellipsis(e.getHint(), 80));
            }
            case CUSTOM -> {
                CustomEvent e = (CustomEvent) event;
                System.out.printf("%n  [自定义] %s → %s%n", e.getName(), e.getValue());
            }

            default ->
                    System.out.printf("%n  [? 未知事件: %s]%n", event.getType());
        }
    }

    private static String iconForState(ToolResultState state) {
        return switch (state) {
            case SUCCESS -> "✅";
            case ERROR -> "❌";
            case INTERRUPTED -> "⏸";
            case DENIED -> "🚫";
            case RUNNING -> "⏳";
        };
    }

    private static String ellipsis(String s, int maxLen) {
        if (s == null) {
            return "null";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
