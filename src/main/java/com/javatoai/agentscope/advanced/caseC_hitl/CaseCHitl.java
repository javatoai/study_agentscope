package com.javatoai.agentscope.advanced.caseC_hitl;

import com.javatoai.agentscope.support.AgentConfig;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.tool.TimeTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 案例 C —— HITL (Human-In-The-Loop) 敏感操作确认。
 *
 * <p>Agent 在执行敏感工具前，通过事件流检测并等待用户确认后才继续。
 *
 * <p>核心概念:
 * <ul>
 *   <li>{@code streamEvents} 事件检测</li>
 *   <li>工具调用前的用户确认拦截</li>
 *   <li>取消/继续流程</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseC_hitl.CaseCHitl
 * }</pre>
 */
public final class CaseCHitl {

    private CaseCHitl() {}

    /** 要保护的敏感工具名称列表 */
    private static final java.util.Set<String> SENSITIVE_TOOLS =
            java.util.Set.of("clear_all_data", "delete_user");

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case C · HITL 敏感操作确认",
                "检测敏感工具调用 → 弹确认 → 用户回复 → 继续/取消");

        // 注册一个"危险"工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SensitiveTools());

        ReActAgent agent = ReActAgent.builder()
                .name("hitl-demo")
                .sysPrompt("""
                        你是一个有敏感操作权限的助手。
                        当被要求执行危险操作时，使用 clear_all_data 工具。
                        在执行敏感工具前必须先征求用户确认。
                        请用中文回复。
                        """)
                .model(ModelSupport.defaultModel())
                .toolkit(toolkit)
                .build();

        AtomicBoolean userApproved = new AtomicBoolean(false);

        // 用 streamEvents 实现 HITL
        System.out.println("[系统] 启动 HITL 监控...");
        System.out.println();

        agent.streamEvents(
                List.of(new UserMessage("""
                        我想测试一下数据清理功能，请调用 clear_all_data 工具，
                        参数 table_name=test_logs, reason=测试清理。
                        """)),
                RuntimeContext.empty())
                .doOnNext(event -> {
                    // 每当 Agent 要调工具时，检查是否是敏感工具
                    if (event instanceof ToolCallStartEvent toolStart) {
                        String toolName = toolStart.getToolCallName();
                        if (SENSITIVE_TOOLS.contains(toolName)) {
                            System.out.println();
                            System.out.println("⚠  =================================================");
                            System.out.println("  敏感工具调用: " + toolName);
                            System.out.println("  工具名: " + toolStart.getToolCallName());
                            System.out.println("==================================================");
                            System.out.print("  是否同意执行? (y/n): ");

                            Scanner scanner = new Scanner(System.in);
                            String input = scanner.nextLine().trim().toLowerCase();
                            if (input.equals("y") || input.equals("yes")) {
                                userApproved.set(true);
                                System.out.println("  ✅ 已批准执行");
                            } else {
                                System.out.println("  ❌ 已拒绝执行");
                            }
                            System.out.println();
                        }
                    }

                    // 打印文本增量
                    if (event.getType() == io.agentscope.core.event.AgentEventType.TEXT_BLOCK_DELTA) {
                        System.out.print(((io.agentscope.core.event.TextBlockDeltaEvent) event).getDelta());
                    }
                })
                .doOnComplete(() -> System.out.println())
                .blockLast();

        System.out.println();
        System.out.println("[HITL 结果] 用户批准状态: " + (userApproved.get() ? "已批准" : "已拒绝"));
        System.out.println(">>> Case C 完成：工具拦截、用户决策、安全边界");
    }
}

// ── 敏感工具定义 ──

/**
 * 模拟敏感操作的工具类。
 */
class SensitiveTools {

    @io.agentscope.core.tool.Tool(
            name = "clear_all_data",
            description = "⚠ 危险操作：清空数据库表中的所有数据。需要用户确认。",
            readOnly = false,
            concurrencySafe = false)
    public String clearAllData(
            @io.agentscope.core.tool.ToolParam(name = "table_name", description = "目标表名")
            String tableName,
            @io.agentscope.core.tool.ToolParam(name = "reason", description = "清空原因")
            String reason) {
        return "已清空表 " + tableName + "（原因: " + reason + "）[模拟操作]";
    }
}
