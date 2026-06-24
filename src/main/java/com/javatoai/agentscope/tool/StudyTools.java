package com.javatoai.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 学习笔记工具：演示 Agent 通过多步工具调用维护会话内业务状态。
 *
 * <p>笔记按 sessionId 隔离，同一 JVM 内跨 call 共享（模拟轻量持久层）。
 */
public final class StudyTools {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConcurrentMap<String, List<String>> notesBySession = new ConcurrentHashMap<>();

    @Tool(
            name = "save_study_note",
            description = "Save a study note for the current learning session.",
            readOnly = false,
            concurrencySafe = false)
    public String saveStudyNote(
            @ToolParam(name = "session_id", description = "Current session id")
            String sessionId,
            @ToolParam(name = "topic", description = "Note topic, e.g. Middleware")
            String topic,
            @ToolParam(name = "content", description = "Note body")
            String content) {
        String entry = "[%s] %s — %s".formatted(
                LocalDateTime.now().format(FORMATTER), topic, content);
        notesBySession
                .computeIfAbsent(sessionId, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(entry);
        return "saved: " + entry;
    }

    @Tool(
            name = "list_study_notes",
            description = "List all study notes saved in the current session.",
            readOnly = true,
            concurrencySafe = true)
    public String listStudyNotes(
            @ToolParam(name = "session_id", description = "Current session id")
            String sessionId) {
        List<String> notes = notesBySession.getOrDefault(sessionId, List.of());
        if (notes.isEmpty()) {
            return "No notes yet for session " + sessionId;
        }
        return String.join("\n", notes);
    }
}
