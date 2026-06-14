package com.javatoai.agentscope.support;

/**
 * 所有学习示例共用的环境检查与终端输出工具。
 */
public final class EnvSupport {

    private EnvSupport() {
    }

    /**
     * 校验 API Key 是否已配置（与 study-langchain 相同，读取 {@code API_KEY_ZAI}）。
     *
     * <p>同时触发 {@link ModelSupport} 类加载，完成 {@code zai:*} 的 ModelRegistry 注册。
     */
    public static void requireApiKey() {
        String modelId = ModelSupport.DEFAULT_MODEL_ID;
        String apiKey = System.getenv(ModelSupport.API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("""
                    [ERROR] 未检测到环境变量 API_KEY_ZAI

                    与 study-langchain 项目使用同一套配置，例如：
                      PowerShell:  $env:API_KEY_ZAI="your-key"
                      Bash:        export API_KEY_ZAI=your-key

                    参考: Q:\\workspace_pycharm\\study-langchain\\llm_config.py
                    默认模型: %s
                    """.formatted(modelId));
            System.exit(1);
        }
        ModelSupport.ensureRegistered();
    }

    /** @deprecated 请使用 {@link #requireApiKey()} */
    @Deprecated
    public static void requireDeepSeekApiKey() {
        requireApiKey();
    }

    /** @deprecated 请使用 {@link #requireApiKey()} */
    @Deprecated
    public static void requireDashScopeApiKey() {
        requireApiKey();
    }

    public static void printBanner(String lesson, String goal) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  " + lesson);
        System.out.println("  目标: " + goal);
        System.out.println("=".repeat(60));
        System.out.println("  模型: " + AgentConfig.DEFAULT_MODEL);
        System.out.println("  Key : " + ModelSupport.API_KEY_ENV);
        System.out.println("=".repeat(60));
        System.out.println();
    }
}
