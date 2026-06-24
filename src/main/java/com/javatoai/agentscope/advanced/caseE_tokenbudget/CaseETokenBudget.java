package com.javatoai.agentscope.advanced.caseE_tokenbudget;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 案例 E —— Token 预算控制器 Middleware。
 *
 * <p>一个 Middleware 统计每次模型调用的 token 消耗（估算），
 * 超出预算后自动注入强制终止指令，防止无限推理。
 *
 * <p>核心概念:
 * <ul>
 *   <li>{@code onModelCall} 洋葱模式深度使用</li>
 *   <li>跨调用状态维护（累计 token）</li>
 *   <li>预算耗尽后的优雅降级</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseE_tokenbudget.CaseETokenBudget
 * }</pre>
 */
public final class CaseETokenBudget {

    private CaseETokenBudget() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case E · Token Budget Controller",
                "Middleware 累计 token → 超预算自动终止");

        long budgetTokens = 300;  // 模拟低预算
        TokenBudgetMiddleware budgetMdw = new TokenBudgetMiddleware(budgetTokens);

        ReActAgent agent = ReActAgent.builder()
                .name("budget-demo")
                .sysPrompt("""
                        你是一个有用的助手。请详细回答问题。
                        """)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .middlewares(List.of(budgetMdw))
                .build();

        System.out.println("[预算] 总额: " + budgetTokens + " tokens (模拟低预算)");
        System.out.println();

        Msg reply = agent.call(
                List.of(new UserMessage("请详细介绍一下 Java 的垃圾回收机制（越详细越好）")),
                RuntimeContext.empty())
                .block();

        System.out.println();
        System.out.println("[最终回复] " + reply.getTextContent());
        System.out.println();
        budgetMdw.printReport();
        System.out.println();
        System.out.println(">>> Case E 完成：累计 token、超预算拦截、优雅降级");
    }
}

// ── Token 预算 Middleware ──

class TokenBudgetMiddleware implements MiddlewareBase {

    private final long budgetTokens;
    private final AtomicLong totalTokensUsed = new AtomicLong(0);
    private final AtomicLong callCount = new AtomicLong(0);

    /** 超预算时注入的终止指令 */
    private static final String BUDGET_EXHAUSTED_HINT =
            "\n\n[系统] Token 预算已耗尽。请立即停止推理，给出简短总结。";

    TokenBudgetMiddleware(long budgetTokens) {
        this.budgetTokens = budgetTokens;
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, ModelCallInput input, Function<ModelCallInput, Flux<AgentEvent>> next) {

        long used = totalTokensUsed.get();
        if (used >= budgetTokens) {
            System.out.println("  ⛔ [Budget] 预算已耗尽 (" + used + "/" + budgetTokens + "), 跳过本次模型调用");
            return Flux.empty();  // 不再请求模型
        }

        System.out.println("  💰 [Budget] 第 " + callCount.incrementAndGet()
                + " 次模型调用, 已用 " + used + "/" + budgetTokens + " tokens");

        // 估算 token：假设这次请求的字符数 / 2 ≈ token 数（粗略估计）
        long estimatedTokens = input.messages().stream()
                .mapToLong(m -> m.getTextContent().length())
                .sum() / 2;

        return next.apply(input)
                .doOnNext(event -> {
                    totalTokensUsed.addAndGet(estimatedTokens + 50); // +50 估算回复 token
                })
                .doOnComplete(() -> {
                    if (totalTokensUsed.get() >= budgetTokens) {
                        System.out.println("  ⚠ [Budget] 本次调用后预算耗尽");
                    }
                });
    }

    void printReport() {
        System.out.println("[预算报告] 总额: " + budgetTokens
                + " | 估算消耗: " + totalTokensUsed.get()
                + " | 模型调用次数: " + callCount.get());
    }
}
