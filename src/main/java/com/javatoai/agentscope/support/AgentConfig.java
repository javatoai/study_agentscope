package com.javatoai.agentscope.support;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AgentScope 2.0 学习示例的全局常量。
 *
 * <p>默认模型与 {@code study-langchain/llm_config.py} 保持一致：
 * {@code API_KEY_ZAI} + 智谱 OpenAI 兼容端点 + {@code glm-5}。
 */
public final class AgentConfig {

    /** 默认模型 ID，对应智谱 glm-5。 */
    public static final String DEFAULT_MODEL = ModelSupport.DEFAULT_MODEL_ID;

    public static final Path WORKSPACE_PATH = Paths.get(".agentscope/workspace");

    public static final String STATE_HOME =
            System.getProperty("user.home") + "/.agentscope/state";

    public static final Path SESSION_STORE_PATH = Paths.get(
            System.getProperty("user.home"), ".agentscope", "sessions");

    public static final String DEMO_SESSION_ID = "study-demo-session";

    public static final String DEMO_USER_ID = "zhen.wang";

    private AgentConfig() {
    }
}
