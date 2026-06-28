package com.javatoai.agentscope.advanced.caseF_safety;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 案例 F —— 内容安全过滤器 Middleware。
 *
 * <p>在 {@code onAgent} 洋葱模式中扫描输出文本，检测到敏感词后替换。
 * 同时记录审计日志，展示被阻断次数。
 *
 * <p><b>注意:</b> {@code TextBlockDeltaEvent} 不可变，本案例通过打印
 * 警告并记录日志来演示内容审查模式，而非尝试构造新的事件实例。
 *
 * <p>核心概念:
 * <ul>
 *   <li>{@code onAgent} 洋葱模式深度使用</li>
 *   <li>事件流中扫描文本增量并标记</li>
 *   <li>审计日志记录</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseF_safety.CaseFSafetyFilter
 * }</pre>
 */
public final class CaseFSafetyFilter {

    private CaseFSafetyFilter() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case F · Content Safety Filter",
                "onAgent 拦截输出 → 敏感词检测 → 审计日志");

        ContentFilterMiddleware filterMdw = new ContentFilterMiddleware(
                Set.of("password", "secret_key", "internal-api"));

        ReActAgent agent = ReActAgent.builder()
                .name("content-demo")
                .sysPrompt("""
                        你是一个开发者助手。
                        当被问到配置相关问题时，你可能会提到配置键名。
                        请用中文回复。
                        """)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .middlewares(List.of(filterMdw))
                .build();

        System.out.println("[安全过滤器] 已加载敏感词: " + String.join(", ", filterMdw.blockedWords()));
        System.out.println();

        Msg reply = agent.call(
                List.of(new UserMessage("""
                        请给一个示例配置文件，它包含数据库连接设置。
                        可以提及常见的配置键名如 password、host 等。
                        """)),
                RuntimeContext.empty())
                .block();

        System.out.println();
        System.out.println("[最终回复（已完成扫描）]");
        System.out.println(reply.getTextContent());
        System.out.println();
        filterMdw.printAuditReport();
        System.out.println();
        System.out.println(">>> Case F 完成：内容扫描、敏感词检测、审计日志");
    }
}

// ── 内容过滤 Middleware ──

class ContentFilterMiddleware implements MiddlewareBase {

    private final Set<String> blockedWords;
    private final AtomicInteger blockCount = new AtomicInteger(0);
    private static final String REPLACEMENT = "[REDACTED]";

    ContentFilterMiddleware(Set<String> blockedWords) {
        this.blockedWords = blockedWords;
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext rc, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {

        System.out.println("  [Security] 开始扫描...");
        return next.apply(input)
                .doOnNext(event -> {
                    if (event instanceof TextBlockDeltaEvent delta) {
                        String original = delta.getDelta();
                        String filtered = censor(original);
                        if (!filtered.equals(original)) {
                            blockCount.incrementAndGet();
                            System.out.println("  [Security] 检测到敏感词, 已标记 (原文含: "
                                    + findMatches(original) + ")");
                        }
                    }
                })
                .doOnComplete(() -> System.out.println("  [Security] 扫描完成"));
    }

    private String censor(String text) {
        String result = text;
        for (String word : blockedWords) {
            result = result.replace(word, REPLACEMENT);
        }
        return result;
    }

    private String findMatches(String text) {
        return blockedWords.stream()
                .filter(text::contains)
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    Set<String> blockedWords() {
        return blockedWords;
    }

    void printAuditReport() {
        System.out.println("[审计报告] 敏感词: " + String.join(", ", blockedWords)
                + " | 命中次数: " + blockCount.get()
                + " | 替换为: " + REPLACEMENT);
    }
}
