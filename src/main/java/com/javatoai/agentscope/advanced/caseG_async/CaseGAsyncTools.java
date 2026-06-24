package com.javatoai.agentscope.advanced.caseG_async;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 案例 G —— 异步工具与工具链。
 *
 * <p>工具返回 {@link Mono} 而非同步值：异步查库、API 调用，
 * 以及一个工具的输出作为下一个工具的输入，形成工具管道。
 *
 * <p>核心概念:
 * <ul>
 *   <li>{@code @Tool} 返回 {@link Mono<String>}</li>
 *   <li>反应式工具管道（Reactor 链）</li>
 *   <li>模拟异步延迟</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseG_async.CaseGAsyncTools
 * }</pre>
 */
public final class CaseGAsyncTools {

    private CaseGAsyncTools() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case G · Async Tools & Tool Chain",
                "Mono 返回 → 异步工具 → 工具管道");

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new AsyncDbTools());
        toolkit.registerTool(new AsyncApiTools());

        ReActAgent agent = ReActAgent.builder()
                .name("async-demo")
                .sysPrompt("""
                        你是一个能访问异步工具的助手。
                        先用 fetch_order_status(id) 查订单状态，如果状态是 "pending"，
                        再调用 enrich_customer_data(订单中的customer_id) 获取更多上下文。
                        最后调用 generate_report(摘要) 生成报告。
                        请用中文回复。
                        """)
                .model(ModelSupport.defaultModel())
                .toolkit(toolkit)
                .build();

        System.out.println("工具说明:");
        System.out.println("  fetch_order_status    → 异步查库 (模拟 200ms)");
        System.out.println("  enrich_customer_data  → 异步调 API (模拟 150ms)");
        System.out.println("  generate_report       → 异步生成报告 (模拟 100ms)");
        System.out.println("  推荐调用链: fetch → enrich → generate");
        System.out.println();

        Msg reply = agent.call(
                List.of(new UserMessage("""
                        请按以下流程操作：
                        1) 先查订单 ORD-2024 的状态 (fetch_order_status)
                        2) 再根据订单中的 customer_id 获取客户详情 (enrich_customer_data)
                        3) 最后生成一份报告 (generate_report)
                        请依次调用工具并报告每一步结果。
                        """)),
                RuntimeContext.empty()).block();

        System.out.println("[Agent] " + reply.getTextContent());
        System.out.println();
        System.out.println(">>> Case G 完成：异步工具、Mono 返回、工具管道串联");
    }
}

// ── 模拟异步数据库工具 ──

class AsyncDbTools {

    @Tool(
            name = "fetch_order_status",
            description = "异步：从数据库查询订单状态。",
            readOnly = true,
            concurrencySafe = true)
    public Mono<String> fetchOrderStatus(
            @ToolParam(name = "order_id", description = "订单ID，如 ORD-2024")
            String orderId) {
        System.out.println("  ⏳ [fetch_order_status] 开始异步查询 " + orderId + "...");
        return Mono.fromCallable(() -> {
            // 模拟数据库查询延迟
            Thread.sleep(200);
            System.out.println("  ✅ [fetch_order_status] 查询完成");
            return String.format("订单 %s → 状态=pending, customer_id=CUS-889, 金额=¥1280", orderId);
        });
    }
}

// ── 模拟异步 API 工具 ──

class AsyncApiTools {

    @Tool(
            name = "enrich_customer_data",
            description = "异步：从外部 API 查询并补充客户详细信息。",
            readOnly = true,
            concurrencySafe = true)
    public Mono<String> enrichCustomerData(
            @ToolParam(name = "customer_id", description = "客户ID，如 CUS-889")
            String customerId) {
        System.out.println("  ⏳ [enrich_customer_data] 开始异步查询 " + customerId + "...");
        return Mono.just(customerId)
                .delayElement(Duration.ofMillis(150))
                .map(id -> {
                    System.out.println("  ✅ [enrich_customer_data] 查询完成");
                    return String.format("客户 %s → 等级=VIP, 区域=华东, 历史订单=12笔", id);
                });
    }

    @Tool(
            name = "generate_report",
            description = "异步：生成总结报告。",
            readOnly = true,
            concurrencySafe = true)
    public Mono<String> generateReport(
            @ToolParam(name = "summary", description = "报告内容")
            String summary) {
        System.out.println("  ⏳ [generate_report] 开始生成报告...");
        return Mono
                .fromCallable(() -> {
                    Thread.sleep(100);
                    return "📄 报告已生成: " + summary;
                })
                .doOnSuccess(r -> System.out.println("  ✅ [generate_report] 完成"));
    }
}
