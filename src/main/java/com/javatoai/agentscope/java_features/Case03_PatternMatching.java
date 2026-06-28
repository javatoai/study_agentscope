package com.javatoai.agentscope.java_features;

/**
 * <h2>Java 模式匹配（Pattern Matching + Switch）</h2>
 *
 * <pre>
 *   模式匹配是跨多个 Java 版本逐步推出的特性家族：
 *     - Java 14/16: {@code instanceof} 模式匹配 (JEP 394)
 *     - Java 14→17: Switch 表达式 (JEP 361)
 *     - Java 17: 密封类的 switch (JEP 409)
 *     - Java 19/20: Record 模式 (JEP 440)
 *     - Java 21: Switch 模式匹配正式版 (JEP 441)
 * </pre>
 *
 * <h3>关键概念</h3>
 * <ul>
 *   <li>{@code instanceof} 模式 —— {@code if (obj instanceof String s)} 一步完成类型检查+绑定</li>
 *   <li>Switch 表达式 —— 有返回值、箭头语法、无穿透</li>
 *   <li>密封类 + switch —— 编译器检查穷举性，无需 default</li>
 *   <li>Guarded Pattern —— {@code case X x when x.value > 0}</li>
 *   <li>Record 模式 —— 解构 Record 字段</li>
 * </ul>
 */
public final class Case03_PatternMatching {

    // ═══════════════════════════════════════════════════════════════
    // 复用 Case02 的 Sealed 体系
    // ═══════════════════════════════════════════════════════════════

    sealed interface ToolResult
            permits ToolSuccess, ToolFailure, ToolPending {
        String toolName();
    }

    record ToolSuccess(String toolName, String output) implements ToolResult {}
    record ToolFailure(String toolName, String error, int errorCode) implements ToolResult {}

    /** ToolPending 用 sealed class 不是 interface —— 展示不同写法 */
    non-sealed interface ToolPending extends ToolResult {
        int timeoutSeconds();
        String status();
    }

    record AsyncPending(String toolName, int timeoutSeconds) implements ToolPending {
        @Override
        public String status() { return "等待中"; }
    }

    record LongRunning(String toolName, int timeoutSeconds) implements ToolPending {
        @Override
        public String status() { return "长时间运行"; }
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. instanceof 模式匹配（Java 16）
    // ═══════════════════════════════════════════════════════════════

    static void demoInstanceof(Object obj) {
        System.out.println("\n  ——— instanceof 模式匹配 ———");

        // ❌ 旧写法： instanceof + 强制转型（重复）
        if (obj instanceof String) {
            String s = (String) obj;
            System.out.println("  旧写法: 长度=" + s.length());
        }

        // ✅ 新写法：一步完成检查 + 绑定变量
        if (obj instanceof String s) {
            System.out.println("  新写法: 长度=" + s.length() + ", 内容=" + s);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Switch 表达式（Java 14）
    // ═══════════════════════════════════════════════════════════════

    static String describeStatusCode(int status) {
        return switch (status) {                              // 有返回值
            case 200, 201      -> "成功";                     // 箭头语法，无穿透
            case 301, 302      -> "重定向";
            case 400           -> "客户端错误(400)";
            case 401, 403      -> {
                System.out.println("    [日志] 鉴权失败");      // 代码块 + yield
                yield "鉴权失败";
            }
            case 500, 502, 503 -> "服务器错误";
            default            -> "未知状态: " + status;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. 穷举 Switch（Sealed Class + Switch）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 因为 ToolResult 是 sealed，编译器知道所有可能的子类型，
     * 所以这个 switch 不需要 default —— 如果漏了某个类型，编译报错。
     */
    static String handleToolResult(ToolResult result) {
        return switch (result) {
            case ToolSuccess s ->
                    "✅ " + s.toolName() + " 成功: " + s.output();
            case ToolFailure f ->
                    "❌ " + f.toolName() + " 失败[code=" + f.errorCode() + "]: " + f.error();
            case ToolPending p ->
                    "⏳ " + p.toolName() + " 等待中(" + p.timeoutSeconds() + "s): " + p.status();
            // 没有 default —— 编译器帮你检查覆盖
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Guarded Pattern —— case + when（Java 21 正式）
    // ═══════════════════════════════════════════════════════════════

    static String classifyResult(ToolResult result) {
        return switch (result) {
            case ToolSuccess s
                    when s.output().length() > 20  -> "成功（长结果，需折叠）";
            case ToolSuccess s
                    when s.output().length() <= 20 -> "成功（短结果）";
            case ToolFailure f
                    when f.errorCode() >= 500      -> "服务端错误，需重试";
            case ToolFailure f                     -> "客户端错误";
            case ToolPending p
                    when p.timeoutSeconds() > 30   -> "长超时任务";
            default                                -> "未知状态";
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Record 模式 —— 解构（Java 21）
    // ═══════════════════════════════════════════════════════════════

    /**
     * Record 模式可以直接解构出字段，不用 s.output() / f.errorCode()
     */
    static String deconstructResult(ToolResult result) {
        return switch (result) {
            // 解构 Record: ToolSuccess(String toolName, String output)
            case ToolSuccess(String name, String output)
                    when output.length() > 30 ->
                    name + " 成功(长): " + output.substring(0, 30) + "...";

            case ToolSuccess(String name, String output) ->
                    name + " 成功: " + output;

            // ToolFailure 也解构
            case ToolFailure(String name, String error, int code)
                    when code >= 500 ->
                    name + " 服务端错误: " + error;

            case ToolFailure(String name, String error, int code) ->
                    name + " 客户端错误(" + code + "): " + error;

            // ToolPending 是 non-sealed interface，子类也可以解构
            case AsyncPending(String name, int timeout) ->
                    "异步 " + name + ", 超时=" + timeout + "s";

            case LongRunning(String name, int timeout) ->
                    "长时间 " + name + ", 超时=" + timeout + "s";

            // 兜底：non-sealed 可能还有其它实现
            case ToolPending p ->
                    "未知等待: " + p.toolName();
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. 模拟 AgentScope 真实场景 —— StreamEventPrinter 的 switch
    // ═══════════════════════════════════════════════════════════════

    enum EventType {
        TEXT_DELTA, TOOL_CALL_START, TOOL_RESULT_END,
        MODEL_CALL_END, AGENT_START, AGENT_END
    }

    record Event(EventType type, String payload) {}

    static String formatEvent(Object event) {
        return switch (event) {
            // Record 模式解构 + guarded pattern 过滤
            case Event(EventType type, String delta)
                    when type == EventType.TEXT_DELTA && !delta.isBlank() -> "📝 " + delta;

            case Event(EventType type, String delta2)
                    when type == EventType.TEXT_DELTA -> "";  // 空白 delta 静默

            case Event(EventType type, String name)
                    when type == EventType.TOOL_CALL_START -> "🔧 调用工具: " + name;

            case Event(EventType type, String state)
                    when type == EventType.TOOL_RESULT_END -> "  结果: " + state;

            case Event(EventType type, String usageJson)
                    when type == EventType.MODEL_CALL_END && !usageJson.isBlank() ->
                    "📊 模型调用结束: " + usageJson;

            case Event(EventType type, String name)
                    when type == EventType.AGENT_START -> "▶ Agent " + name + " 启动";

            case Event(EventType type, String payload)
                    when type == EventType.AGENT_END -> "◼ Agent 结束";

            default -> "未知事件: " + event;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // main
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("=".repeat(55));
        System.out.println("  1. instanceof 模式匹配");
        System.out.println("=".repeat(55));
        demoInstanceof("Hello Record Pattern!");
        demoInstanceof(42);

        System.out.println("\n" + "=".repeat(55));
        System.out.println("  2. Switch 表达式");
        System.out.println("=".repeat(55));
        System.out.println("  200 → " + describeStatusCode(200));
        System.out.println("  302 → " + describeStatusCode(302));
        System.out.println("  400 → " + describeStatusCode(400));
        System.out.println("  401 → " + describeStatusCode(401));
        System.out.println("  503 → " + describeStatusCode(503));
        System.out.println("  418 → " + describeStatusCode(418));

        System.out.println("\n" + "=".repeat(55));
        System.out.println("  3. Sealed Class 穷举 Switch（无需 default）");
        System.out.println("=".repeat(55));
        ToolResult[] results = {
                new ToolSuccess("get_weather", "北京晴天，温度20度"),
                new ToolSuccess("long_query", "这条结果非常非常非常非常长长长长长长长长长长"),
                new ToolFailure("calc", "除零错误", 500),
                new ToolFailure("delete", "权限不足", 403),
                new AsyncPending("gen_report", 15),
                new AsyncPending("gen_report", 60),
                new LongRunning("ml_train", 300),
        };
        for (ToolResult r : results) {
            System.out.println("  " + handleToolResult(r));
        }

        System.out.println("\n" + "=".repeat(55));
        System.out.println("  4. Guarded Pattern（case + when）");
        System.out.println("=".repeat(55));
        for (ToolResult r : results) {
            System.out.println("  " + classifyResult(r));
        }

        System.out.println("\n" + "=".repeat(55));
        System.out.println("  5. Record 模式（解构）");
        System.out.println("=".repeat(55));
        for (ToolResult r : results) {
            System.out.println("  " + deconstructResult(r));
        }

        System.out.println("\n" + "=".repeat(55));
        System.out.println("  6. 模拟 AgentScope StreamEventPrinter");
        System.out.println("=".repeat(55));
        Object[] events = {
                new Event(EventType.AGENT_START, "weather-agent"),
                new Event(EventType.TOOL_CALL_START, "get_weather"),
                new Event(EventType.TEXT_DELTA, "今天北京天气不错"),
                new Event(EventType.TEXT_DELTA, "    "),              // 空白 —— 静默
                new Event(EventType.TOOL_RESULT_END, "SUCCESS"),
                new Event(EventType.MODEL_CALL_END, "{\"in\":100,\"out\":50}"),
                new Event(EventType.AGENT_END, ""),
        };
        for (Object e : events) {
            String s = formatEvent(e);
            if (!s.isEmpty()) {
                System.out.println("  " + s);
            }
        }

        System.out.println("\n>>> Case03_PatternMatching 完成");
    }
}
