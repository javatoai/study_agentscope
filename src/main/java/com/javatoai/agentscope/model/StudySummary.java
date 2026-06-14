package com.javatoai.agentscope.model;

import java.util.List;

/**
 * Lesson 06 结构化输出的 Schema 定义。
 *
 * <p>AgentScope 2.0 会根据该 record 自动生成 JSON Schema，
 * 并在 {@code call(..., StudySummary.class)} 返回后通过
 * {@code Msg.getStructuredData(StudySummary.class)} 反序列化。
 *
 * @param topic      学习主题
 * @param keyPoints  关键要点列表
 * @param difficulty 难度评估：easy / medium / hard
 */
public record StudySummary(
        String topic,
        List<String> keyPoints,
        String difficulty) {
}
