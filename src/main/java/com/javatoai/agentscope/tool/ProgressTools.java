package com.javatoai.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lesson 09 使用的学习进度工具。
 */
public final class ProgressTools {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, String> progressBySession = new ConcurrentHashMap<>();

    @Tool(
            name = "record_progress",
            description = "记录当前学习进度。",
            readOnly = false,
            concurrencySafe = false)
    public String recordProgress(
            @ToolParam(name = "session_id", description = "会话ID") String sessionId,
            @ToolParam(name = "topic", description = "学习主题") String topic,
            @ToolParam(name = "status", description = "进度状态: started/in_progress/done") String status) {
        String entry = "[%s] %s → %s".formatted(LocalDateTime.now().format(FMT), topic, status);
        progressBySession.put(sessionId, topic);
        return "已记录: " + entry;
    }

    @Tool(
            name = "check_progress",
            description = "查看当前会话的学习进度。",
            readOnly = true,
            concurrencySafe = true)
    public String checkProgress(
            @ToolParam(name = "session_id", description = "会话ID") String sessionId) {
        String topic = progressBySession.get(sessionId);
        if (topic == null) {
            return "暂无学习记录";
        }
        return "当前学习: " + topic;
    }

    @Tool(
            name = "get_timestamp",
            description = "获取当前时间戳。",
            readOnly = true,
            concurrencySafe = true)
    public String getTimestamp() {
        return LocalDateTime.now().format(FMT);
    }
}
