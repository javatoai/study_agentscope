package com.javatoai.agentscope.advanced.caseD_approval;

import com.javatoai.agentscope.support.AgentConfig;
import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

/**
 * 案例 D —— 多步骤审批工作流。
 *
 * <p>通过 Middleware 拦截每个工具调用，在关键步骤前要求用户审批。
 * 支持两种模式：「逐步确认」和「全部同意」。
 *
 * <p>核心概念:
 * <ul>
 *   <li>Middleware {@code onActing} 洋葱模式深度使用</li>
 *   <li>{@code ActingInput} 拦截并操作工具调用</li>
 *   <li>状态驱动的审批策略</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseD_approval.CaseDApprovalWorkflow
 * }</pre>
 */
public final class CaseDApprovalWorkflow {

    private CaseDApprovalWorkflow() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case D · Multi-Step Approval Workflow",
                "Middleware 拦截每个工具调用 → 逐步审批");

        System.out.print("审批模式 (1=逐步确认, 2=全部同意): ");
        Scanner scanner = new Scanner(System.in);
        int mode = scanner.nextInt();
        scanner.nextLine(); // consume newline

        ApprovalMiddleware approvalMdw = new ApprovalMiddleware(
                mode == 1 ? ApprovalMode.STEP_BY_STEP : ApprovalMode.APPROVE_ALL,
                scanner);

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WorkflowTools());

        HarnessAgent agent = HarnessAgent.builder()
                .name("workflow-agent")
                .sysPrompt("""
                        你是一个工作流助手。当被要求执行一系列操作时，
                        请按顺序调用工具：prepare_data → validate_data → publish_data。
                        请用中文回复。
                        """)
                .model(ModelSupport.defaultModel())
                .workspace(AgentConfig.WORKSPACE_PATH)
                .toolkit(toolkit)
                .middlewares(List.of(approvalMdw))
                .build();

        Msg reply = agent.call(
                List.of(new UserMessage("""
                        请按顺序执行数据处理工作流：
                        1) prepare_data (dataset=users, rows=1000)
                        2) validate_data (dataset=users, strict=true)
                        3) publish_data (dataset=users, target=production)
                        """)),
                RuntimeContext.builder()
                        .userId(AgentConfig.DEMO_USER_ID)
                        .sessionId("workflow-session")
                        .build())
                .block();

        System.out.println("[最终回复] " + reply.getTextContent());
        System.out.println();
        System.out.println(">>> Case D 完成：Middleware 逐步审批、ActingInput 拦截");
    }
}

// ── 审批模式 ──

enum ApprovalMode { STEP_BY_STEP, APPROVE_ALL }

// ── 审批 Middleware ──

class ApprovalMiddleware implements MiddlewareBase {

    private final ApprovalMode mode;
    private final Scanner scanner;
    private boolean approvedAll = false;

    ApprovalMiddleware(ApprovalMode mode, Scanner scanner) {
        this.mode = mode;
        this.scanner = scanner;
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent, ActingInput input, Function<ActingInput, Flux<AgentEvent>> next) {

        String toolNames = input.toolCalls().stream()
                .map(ToolUseBlock::getName)
                .reduce((a, b) -> a + ", " + b).orElse("none");

        System.out.println();
        System.out.println("── 审批点 ────────────────────────────");
        System.out.println("  工具: " + toolNames);

        boolean approve;
        switch (mode) {
            case APPROVE_ALL -> {
                if (!approvedAll) {
                    System.out.print("  是否同意后续所有操作? (y/n): ");
                    approvedAll = "y".equalsIgnoreCase(scanner.nextLine().trim());
                }
                approve = approvedAll;
            }
            case STEP_BY_STEP -> {
                System.out.print("  是否同意本次调用? (y/n): ");
                approve = "y".equalsIgnoreCase(scanner.nextLine().trim());
            }
            default -> approve = false;
        }

        if (approve) {
            System.out.println("  ✅ 已批准");
            return next.apply(input);
        } else {
            System.out.println("  ❌ 已拒绝——跳过工具执行");
            return Flux.empty();
        }
    }
}

// ── 工作流工具 ──

class WorkflowTools {

    @Tool(
            name = "prepare_data",
            description = "准备待处理的数据集。",
            readOnly = false,
            concurrencySafe = false)
    public String prepareData(
            @ToolParam(name = "dataset", description = "数据集名称") String dataset,
            @ToolParam(name = "rows", description = "预估行数") int rows) {
        return String.format("数据集 %s 已准备（%d 行）[模拟]", dataset, rows);
    }

    @Tool(
            name = "validate_data",
            description = "校验数据集完整性。",
            readOnly = false,
            concurrencySafe = false)
    public String validateData(
            @ToolParam(name = "dataset", description = "数据集名称") String dataset,
            @ToolParam(name = "strict", description = "是否启用严格校验") boolean strict) {
        return String.format("数据集 %s 验证通过（严格模式=%b）[模拟]", dataset, strict);
    }

    @Tool(
            name = "publish_data",
            description = "将校验通过的数据集发布到目标环境。",
            readOnly = false,
            concurrencySafe = false)
    public String publishData(
            @ToolParam(name = "dataset", description = "数据集名称") String dataset,
            @ToolParam(name = "target", description = "目标环境") String target) {
        return String.format("数据集 %s 已发布到 %s [模拟]", dataset, target);
    }
}
