package com.javatoai.agentscope.java_features;

/**
 * <h2>Sealed Classes 学习案例</h2>
 *
 * <pre>
 *   Sealed Classes 是 Java 15 预览、Java 17 正式的特性。
 *   一句话：显式声明"只有这些子类可以继承我"，编译器帮你检查。
 * </pre>
 *
 * <h3>核心概念</h3>
 * <ul>
 *   <li>{@code sealed} —— 声明一个类"封闭"</li>
 *   <li>{@code permits} —— 列出所有允许的子类</li>
 *   <li>每个允许的子类必须用 {@code final / sealed / non-sealed} 修饰</li>
 *   <li>{@code final} 子类 —— 到此为止，不能再被继承</li>
 *   <li>{@code sealed} 子类 —— 链式封闭，还需声明 permits</li>
 *   <li>{@code non-sealed} 子类 —— 重新开放，任何人可继承</li>
 * </ul>
 *
 * <h3>为什么需要 Sealed Classes</h3>
 * 传统继承是"开放的"——任何类都可以 extends。这意味着：
 * <ol>
 *   <li>你永远不知道运行时会遇到多少个子类</li>
 *   <li>Pattern Matching 时编译器没法帮你检查是否覆盖了所有分支</li>
 * </ol>
 * Sealed Classes 解决了这两个问题：编译器知道完整的子类集合。
 */
public final class Case02_SealedClasses {

    // ═══════════════════════════════════════════════════════════════
    // 1. 真实场景 —— AgentScope 中的 AgentEvent 就是 sealed 体系
    // ═══════════════════════════════════════════════════════════════
    //
    // 在 AgentScope RC4 源码中：
    //   sealed interface AgentEvent
    //       permits AgentStartEvent, AgentEndEvent, TextBlockDeltaEvent, ...
    //
    // 这意味着：编译器知道所有 AgentEvent 的子类型，switch 时可以穷举。

    // ═══════════════════════════════════════════════════════════════
    // 模拟一个"工具调用结果"的 Sealed 体系
    // ═══════════════════════════════════════════════════════════════

    /**
     * 工具调用结果 —— sealed interface，只允许下面 3 种子类型
     */
    sealed interface ToolResult
            permits ToolSuccess, ToolFailure, ToolPending {

        String toolName();
        String summary();
    }

    /** 成功 —— final 不允许再被继承 */
    record ToolSuccess(String toolName, String output) implements ToolResult {
        @Override
        public String summary() {
            return "✅ " + toolName + ": " + (output.length() > 30 ? output.substring(0, 30) + "..." : output);
        }
    }

    /** 失败 —— final 不允许再被继承 */
    record ToolFailure(String toolName, String error, int errorCode) implements ToolResult {
        @Override
        public String summary() {
            return "❌ " + toolName + " [code=" + errorCode + "]: " + error;
        }
    }

    /** 等待中 —— non-sealed 开放，允许任何人继承 */
    non-sealed interface ToolPending extends ToolResult {
        int timeoutSeconds();
    }

    // 因为这个接口是 non-sealed 的，别人可以自由实现
    record AsyncPending(String toolName, int timeoutSeconds) implements ToolPending {
        @Override
        public String summary() {
            return "⏳ " + toolName + " 等待中 (超时=" + timeoutSeconds + "s)";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Sealed Class（不是 interface）示例
    // ═══════════════════════════════════════════════════════════════

    /** 权限检查结果 —— sealed class 而不是 interface */
    sealed abstract static class PermissionResult
            permits PermissionGranted, PermissionDenied {

        abstract String reason();
    }

    final static class PermissionGranted extends PermissionResult {
        private final String permission;

        PermissionGranted(String permission) { this.permission = permission; }

        @Override
        String reason() { return "已授权: " + permission; }
    }

    final static class PermissionDenied extends PermissionResult {
        private final String permission;
        private final String cause;

        PermissionDenied(String permission, String cause) {
            this.permission = permission;
            this.cause = cause;
        }

        @Override
        String reason() { return "拒绝: " + permission + " (原因: " + cause + ")"; }
    }

    // ═══════════════════════════════════════════════════════════════
    // main
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("=".repeat(55));
        System.out.println("  1. Sealed Interface —— 工具调用结果");
        System.out.println("=".repeat(55));

        ToolResult success = new ToolSuccess("get_weather", "北京晴天，温度20°C");
        ToolResult failure = new ToolFailure("calc", "除零错误", 500);
        ToolResult pending = new AsyncPending("long_query", 30);

        printResult(success);
        printResult(failure);
        printResult(pending);

        System.out.println("\n" + "=".repeat(55));
        System.out.println("  2. Sealed Class —— 权限检查");
        System.out.println("=".repeat(55));

        PermissionResult granted = new PermissionGranted("tool:get_weather");
        PermissionResult denied  = new PermissionDenied("tool:delete_data", "需管理员权限");

        System.out.println("  " + granted.reason());
        System.out.println("  " + denied.reason());

        System.out.println("\n" + "=".repeat(55));
        System.out.println("  3. 编译器保证 —— 如果你添加新子类型");
        System.out.println("=".repeat(55));

        System.out.println("  在 ToolResult 的 permits 列表里加一个新类型，");
        System.out.println("  下面 printResult() 的 switch 就会在编译时报错，");
        System.out.println("  提示你 'switch 不再覆盖所有分支' —— 这就是 sealed 的价值。");

        System.out.println("\n" + "=".repeat(55));
        System.out.println("  4. 验证 instanceof 和类型层次");
        System.out.println("=".repeat(55));

        System.out.println("  pending instanceof ToolResult  = " + (pending instanceof ToolResult));
        System.out.println("  pending instanceof ToolPending = " + (pending instanceof ToolPending));
        // sealed interface 本身也能被 instanceof：
        System.out.println("  success instanceof ToolResult = " + (success instanceof ToolResult));

        System.out.println("\n>>> Case02_SealedClasses 完成");
    }

    private static void printResult(ToolResult result) {
        System.out.println("  " + result.summary());
    }
}
