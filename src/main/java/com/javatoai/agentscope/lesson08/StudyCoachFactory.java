package com.javatoai.agentscope.lesson08;

import com.javatoai.agentscope.middleware.DynamicPhaseMiddleware;
import com.javatoai.agentscope.middleware.ModelTimingMiddleware;
import com.javatoai.agentscope.middleware.RequestTraceMiddleware;
import com.javatoai.agentscope.support.AgentConfig;
import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.tool.StudyTools;
import com.javatoai.agentscope.tool.TimeTools;
import com.javatoai.agentscope.tool.UserTools;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 组装「学习教练」复杂 Agent：Harness + 多工具 + Middleware + TaskList。
 */
final class StudyCoachFactory {

    static final String AGENT_NAME = "study-coach";
    static final String SESSION_ID = "study-coach-advanced";

    private static final String SYSTEM_PROMPT = """
            You are an AgentScope Java 2.0 study coach for user zhen.wang.

            Capabilities you MUST use when relevant:
            - todo_write: break learning goals into trackable tasks (use enableTaskList).
            - save_study_note / list_study_notes: persist key insights (pass session_id from context).
            - get_current_time: when user asks about time (timezone Asia/Shanghai).
            - personalized_greeting: greet user with locale from runtime context.

            Rules:
            - Reply in Chinese unless the user writes in English.
            - Prefer calling tools over guessing.
            - Keep answers structured with short bullet points.
            """;

    private StudyCoachFactory() {
    }

    static HarnessAgent build(AtomicReference<String> currentPhase) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new TodoTools());
        toolkit.registerTool(new TimeTools());
        toolkit.registerTool(new UserTools());
        toolkit.registerTool(new StudyTools());

        return HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(toolkit)
                .enableTaskList(true)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(40)
                        .keepMessages(12)
                        .build())
                .middlewares(List.of(
                        new RequestTraceMiddleware(),
                        new ModelTimingMiddleware(),
                        new DynamicPhaseMiddleware(() -> currentPhase.get())))
                .build();
    }
}
