package com.javatoai.agentscope.support;

import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.OpenAIChatModel;

/**
 * 与 {@code study-langchain/llm_config.py} 对齐的模型配置。
 *
 * <p>通过 {@link OpenAIChatModel} 对接智谱 OpenAI 兼容 API。
 */
public final class ModelSupport {

    public static final String API_KEY_ENV = "API_KEY_ZAI";
    public static final String BASE_URL = "https://open.bigmodel.cn/api/paas/v4/";
    public static final String DEFAULT_MODEL_NAME = "glm-5";
    public static final String DEFAULT_MODEL_ID = "zai:" + DEFAULT_MODEL_NAME;
    public static final String MODEL_ID_PREFIX = "zai:";

    private static volatile boolean initialized = false;

    private ModelSupport() {
    }

    /**
     * 注册 {@code zai:*} 到 ModelRegistry，并保证只执行一次。
     *
     * <p>在 {@link EnvSupport#requireApiKey()} 中调用，避免仅靠 static 块时类加载顺序导致未注册。
     */
    public static void ensureRegistered() {
        if (initialized) {
            return;
        }
        synchronized (ModelSupport.class) {
            if (initialized) {
                return;
            }
            ModelRegistry.registerFactory("zai:.*", ModelSupport::resolveZaiModel);
            initialized = true;
        }
    }

    private static Model resolveZaiModel(String modelId) {
        String modelName = modelId.substring(MODEL_ID_PREFIX.length());
        return buildZaiModel(modelName);
    }

    /**
     * 构建 Agent 时推荐使用：直接返回 Model 实例，不依赖 ModelRegistry 字符串解析。
     */
    public static Model defaultModel() {
        ensureRegistered();
        return buildZaiModel(DEFAULT_MODEL_NAME);
    }

    public static OpenAIChatModel buildZaiModel(String modelName) {
        return OpenAIChatModel.builder()
                .apiKey(readApiKey())
                .modelName(modelName)
                .baseUrl(BASE_URL)
                .stream(true)
                .formatter(new OpenAIChatFormatter())
                .build();
    }

    public static String readApiKey() {
        String apiKey = System.getenv(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "环境变量 " + API_KEY_ENV + " 未设置。"
                            + "请与 study-langchain 项目一样配置智谱 API Key。");
        }
        return apiKey;
    }
}
