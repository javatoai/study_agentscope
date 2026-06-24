package com.javatoai.agentscope.flux_study;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Flux / Mono 学习示例 —— 与 AgentScope 场景对齐。
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.flux_study.FluxStudy
 * }</pre>
 */
public final class FluxStudy {

    private FluxStudy() {
    }

    public static void main(String[] args) {
//        demo01_create();
//        demo02_map();
//        demo03_flatMap();
//        demo04_filter();
//        demo05_doOnNext();
        demo06_merge();
//        demo07_zip();
//        demo08_block_vs_subscribe();
//        demo09_error_handling();
//        demo10_streamEvents_simulation();
    }

    // ============================================================
    // 01 · 创建 Flux 的几种方式
    // ============================================================
    static void demo01_create() {
        sep("01 · 创建 Flux");

        // just — 给几个固定元素
        Flux.just("a", "b", "c")
                .doOnNext(s -> System.out.println("  just: " + s))
                .subscribe();

        // fromIterable — 从集合
        Flux.fromIterable(List.of(1, 2, 3))
                .doOnNext(n -> System.out.println("  fromIterable: " + n))
                .subscribe();

        // range — 连续整数
        Flux.range(5, 4)
                .doOnNext(n -> System.out.println("  range: " + n))
                .subscribe();

        // empty — 立即完成，不发任何元素
        Flux.empty()
                .doOnComplete(() -> System.out.println("  empty: done, no elements"))
                .subscribe();
    }

    // ============================================================
    // 02 · map — 一对一转换（同步）
    // ============================================================
    static void demo02_map() {
        sep("02 · map 一对一转换");

        Flux.just("hello", "world")
                .map(String::toUpperCase)
                .doOnNext(s -> System.out.println("  " + s))
                .subscribe();
    }

    // ============================================================
    // 03 · flatMap — 一对一转成另一个 Flux，然后展平（异步）
    // ============================================================
    static void demo03_flatMap() {
        sep("03 · flatMap 异步展平");

        // 每个字符串映射为一个 Flux，flatMap 把它们合并成一个流
        Flux.just("AgentScope", "Java", "2.0")
                .flatMap(word -> Flux.just(word.split("")))
                .doOnNext(ch -> System.out.print(" " + ch))
                .doOnComplete(() -> System.out.println())
                .subscribe();
    }

    // ============================================================
    // 04 · filter — 过滤不想要的元素
    // ============================================================
    static void demo04_filter() {
        sep("04 · filter 过滤");

        Flux.range(1, 10)
                .filter(n -> n % 2 == 0)
                .doOnNext(n -> System.out.println("  偶数: " + n))
                .subscribe();
    }

    // ============================================================
    // 05 · doOnNext / doOnComplete / doFinally — 旁路观测（不改数据）
    // ============================================================
    static void demo05_doOnNext() {
        sep("05 · doOnXxx 旁路观测");

        Flux.just("step1", "step2", "step3")
                .doOnSubscribe(s -> System.out.println("  [开始订阅]"))
                .doOnNext(data -> System.out.println("  [处理中] " + data))
                .doOnComplete(() -> System.out.println("  [完成]"))
                .doFinally(signal -> System.out.println("  [最终信号] " + signal))
                .subscribe();
    }

    // ============================================================
    // 06 · merge — 多个 Flux 并发合并（哪个先到先输出）
    // ============================================================
    static void demo06_merge() {
        sep("06 · merge 并发合并");

        Flux<String> f1 = Flux.just("A1", "A2").delayElements(Duration.ofMillis(10));
        Flux<String> f2 = Flux.just("B1", "B2").delayElements(Duration.ofMillis(5));

        Flux.merge(f1, f2)
                .doOnNext(s -> System.out.println("  " + s))
                .blockLast();  // 等待所有完成
    }

    // ============================================================
    // 07 · zip — 多个 Flux 对齐合并（一对一配对）
    // ============================================================
    static void demo07_zip() {
        sep("07 · zip 对齐合并");

        Flux<String> names = Flux.just("Alice", "Bob", "Zhen");
        Flux<Integer> scores = Flux.just(95, 87, 92);

        Flux.zip(names, scores)
                .map(tuple -> tuple.getT1() + " → " + tuple.getT2() + "分")
                .doOnNext(s -> System.out.println("  " + s))
                .subscribe();
    }

    // ============================================================
    // 08 · block() vs subscribe() — 同步等待 vs 异步订阅
    // ============================================================
    static void demo08_block_vs_subscribe() {
        sep("08 · block vs subscribe");

        // block — 同步等结果（AgentScope 里大量用）
        String result = Mono.just("done").block();
        System.out.println("  block 结果: " + result);

        // subscribe — 异步，不阻塞当前线程
        Mono.just("async-done")
                .subscribe(s -> System.out.println("  subscribe 结果: " + s));

        // blockLast — 等 Flux 全部发完
        List<String> all = Flux.just("x", "y", "z").collectList().block();
        System.out.println("  blockLast 收集: " + all);
    }

    // ============================================================
    // 09 · 错误处理 — onErrorReturn / onErrorResume
    // ============================================================
    static void demo09_error_handling() {
        sep("09 · 错误处理");

        Flux.just("data1", "error_trigger", "data3")
                .flatMap(s -> {
                    if (s.equals("error_trigger")) {
                        return Flux.error(new RuntimeException("模拟异常: " + s));
                    }
                    return Flux.just(s.toUpperCase());
                })
                .onErrorResume(e -> {
                    System.out.println("  捕获: " + e.getMessage());
                    return Flux.just("FALLBACK_VALUE");
                })
                .doOnNext(s -> System.out.println("  " + s))
                .subscribe();
    }

    // ============================================================
    // 10 · 模拟 AgentScope streamEvents 的事件流处理
    // ============================================================
    static void demo10_streamEvents_simulation() {
        sep("10 · streamEvents 模拟");

        // 这就是 AgentScope 里 Flux<AgentEvent> 的简化版
        Flux.just(new FakeEvent("TEXT_BLOCK_DELTA", "您"),
                        new FakeEvent("TEXT_BLOCK_DELTA", "好"),
                        new FakeEvent("TOOL_CALL_START", "get_current_time"),
                        new FakeEvent("TEXT_BLOCK_DELTA", "，"),
                        new FakeEvent("TEXT_BLOCK_DELTA", "当前时间"),
                        new FakeEvent("TEXT_BLOCK_DELTA", "是"),
                        new FakeEvent("TOOL_RESULT_END", "SUCCESS"),
                        new FakeEvent("TEXT_BLOCK_DELTA", " 14:30"))
                .doOnNext(event -> {
                    // 按事件类型分发 — 和你的 StreamEventPrinter 一样
                    switch (event.type) {
                        case "TEXT_BLOCK_DELTA" -> System.out.print(event.data);
                        case "TOOL_CALL_START" ->
                                System.out.printf("%n  ↳ tool: %s%n  ", event.data);
                        case "TOOL_RESULT_END" ->
                                System.out.printf("%n  ↳ tool done (%s)%n  ", event.data);
                    }
                })
                .blockLast();
        System.out.println();
    }

    // --- helpers ---

    private record FakeEvent(String type, String data) {
    }

    private static void sep(String title) {
        System.out.println();
        System.out.println("─".repeat(50));
        System.out.println("  " + title);
        System.out.println("─".repeat(50));
    }
}
