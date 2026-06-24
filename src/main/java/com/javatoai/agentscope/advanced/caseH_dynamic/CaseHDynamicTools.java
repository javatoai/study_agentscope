package com.javatoai.agentscope.advanced.caseH_dynamic;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import com.javatoai.agentscope.tool.TimeTools;
import com.javatoai.agentscope.tool.UserTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 案例 H —— 动态工具注册。
 *
 * <p>不预先注册所有工具，而是根据对话上下文/用户选择动态决定注册哪些工具。
 *
 * <p>核心概念:
 * <ul>
 *   <li>{@link Toolkit} 的动态增删</li>
 *   <li>按需工具选择策略</li>
 *   <li>同一 Agent 实例不同 call 不同工具集</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseH_dynamic.CaseHDynamicTools
 * }</pre>
 */
public final class CaseHDynamicTools {

    private CaseHDynamicTools() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case H · Dynamic Tool Registration",
                "根据用户选择 → 动态注册不同工具集");

        ReActAgent agent = buildAgentWithoutTools();
        Scanner scanner = new Scanner(System.in);

        // 展示可用工具菜单
        System.out.println("可用的工具模块:");
        System.out.println("  [1] 时间工具 (get_current_time)");
        System.out.println("  [2] 用户工具 (personalized_greeting)");
        System.out.println("  [3] 全部注册");
        System.out.println();
        System.out.print("请选择 (1-3): ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline

        // 根据选择动态构建 Toolkit
        Toolkit selectedToolkit = switch (choice) {
            case 1 -> {
                Toolkit t = new Toolkit();
                t.registerTool(new TimeTools());
                System.out.println("✅ 已注册: 时间工具");
                yield t;
            }
            case 2 -> {
                Toolkit t = new Toolkit();
                t.registerTool(new UserTools());
                System.out.println("✅ 已注册: 用户工具");
                yield t;
            }
            case 3 -> {
                Toolkit t = new Toolkit();
                t.registerTool(new TimeTools());
                t.registerTool(new UserTools());
                System.out.println("✅ 已注册: 时间 + 用户工具");
                yield t;
            }
            default -> new Toolkit();
        };

        // 动态替换工具集
        agent = rebuildWithToolkit(agent, selectedToolkit);

        System.out.println();
        System.out.println("[当前注册的工具]");
        System.out.println("  (动态选择的工具集已生效，Agent 可根据工具提示词调用)");
        System.out.println();

        // 验证动态工具是否生效
        System.out.print("输入你的问题: ");
        String question = scanner.nextLine();

        RuntimeContext ctx = RuntimeContext.builder()
                .userId("demo-user")
                .put(com.javatoai.agentscope.support.UserContext.class,
                        new com.javatoai.agentscope.support.UserContext("demo-user", "zh-CN"))
                .build();

        Msg reply = agent.call(List.of(new UserMessage(question)), ctx).block();
        System.out.println("[Agent] " + reply.getTextContent());

        System.out.println();
        System.out.println(">>> Case H 完成：动态工具选择、按需注册、同一实例不同工具集");
    }

    private static ReActAgent buildAgentWithoutTools() {
        return ReActAgent.builder()
                .name("dynamic-tools")
                .sysPrompt("""
                        你是一个助手。请使用当前可用的工具来回答问题。
                        请用中文回复。
                        """)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();
    }

    /** 演示：根据外部条件重建 Agent 的工具集。 */
    private static ReActAgent rebuildWithToolkit(ReActAgent oldAgent, Toolkit newToolkit) {
        return ReActAgent.builder()
                .name(oldAgent.getName())
                .sysPrompt(oldAgent.getSysPrompt())
                .model(oldAgent.getModel())
                .toolkit(newToolkit)
                .build();
    }
}
