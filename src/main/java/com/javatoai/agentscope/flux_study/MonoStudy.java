package com.javatoai.agentscope.flux_study;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Mono 学习示例 —— 与 AgentScope 场景对齐。
 *
 * <p>Mono 是零或一个元素的异步序列。在 AgentScope 里:
 * <ul>
 *   <li>{@code agent.call() → Mono<Msg>} — 一次调用最终产出一个回复</li>
 *   <li>{@code saveStateToSession() → Mono<Void>} — 只关心完成信号，不关心数据</li>
 *   <li>{@code acquireExecution() → Mono<AgentBase>} — 获取执行权作为资源</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.flux_study.MonoStudy
 * }</pre>
 */
public final class MonoStudy {

    private MonoStudy() {}

    public static void main(String[] args) {
        demo01_create();          // 创建 Mono 的 7 种方式
        demo02_map_vs_flatMap(); // map vs flatMap: 为什么 AgentScope 里都是 flatMap
        demo03_filter();         // filter: 条件为假变 Mono.empty()
        demo04_then();           // then: 忽略结果，链式编排
        demo05_zip();            // zip: 等两个 Mono 都完成
        demo06_error();          // onErrorResume / onErrorReturn / defaultIfEmpty
        demo07_block();          // block() 的正确用法和陷阱
        demo08_defer();          // defer: 延迟创建，每次订阅都重新执行
        demo09_using();          // using: AgentBase.call() 的核心——资源获取/释放
        demo10_agent_flow();     // AgentScope 一次 call() 的 Mono 链模拟
    }

    // ════════════════════════════════════════════════════════════
    // 01 · 创建 Mono 的 7 种方式
    // ════════════════════════════════════════════════════════════
    static void demo01_create() {
        sep("01 · 创建 Mono");

        // 1. just — 已知结果（AgentScope: Mono.just(replyMsg) 直接返回）
        Mono.just("hello")
                .subscribe(s -> {
                    System.out.println("  just: " + s);
                    System.out.println(Thread.currentThread().getName());
                });

        // 2. justOrEmpty — 可能为 null，为空等同 empty
        Mono.justOrEmpty(Optional.ofNullable(null))
                .switchIfEmpty(Mono.just("默认值"))
                .subscribe(s -> System.out.println("  justOrEmpty(null): " + s));

        // 3. fromCallable — 包装可能抛异常的同步调用（AgentScope: 异步工具里常见）
        Mono.fromCallable(() -> {
            // 模拟耗时计算或数据库查询
            return "computed_result";
        }).subscribe(s -> System.out.println("  fromCallable: " + s));

        // 4. fromFuture — 已有 CompletableFuture，转成 Mono
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "from_future");
        Mono.fromFuture(future)
                .subscribe(s -> System.out.println("  fromFuture: " + s));

        // 5. fromRunnable — 只有副作用，不产生值（AgentScope: saveStateToSession）
        Mono.fromRunnable(() -> System.out.println("  fromRunnable: 执行了副作用"))
                .subscribe();  // subscribe() 触发执行

        // 6. empty — 立即完成，不发任何元素（AgentScope: no-op 场景）
        Mono.empty()
                .doOnSuccess(v -> System.out.println("  empty: v=" + v))  // v 是 null
                .subscribe();

        // 7. error — 立即失败（AgentScope: 工具执行报错）
        Mono.error(new RuntimeException("模拟失败"))
                .doOnError(e -> System.out.println("  error: " + e.getMessage()))
                .subscribe();  // 不处理会抛出例外，但 doOnError 消耗了
    }

    // ════════════════════════════════════════════════════════════
    // 02 · map vs flatMap — Mono 上最重要的区别
    // ════════════════════════════════════════════════════════════
    static void demo02_map_vs_flatMap() {
        sep("02 · map vs flatMap");

        // map: 同步转换 T → U（不要返回 Mono）
        Mono.just("hello")
                .map(String::toUpperCase)         // String → String
                .map(s -> s.length())             // String → Integer
                .subscribe(n -> System.out.println("  map 结果: " + n));

        // flatMap: 异步转换 T → Mono<U>，然后展平
        // 如果用了 map 返回 Mono，你会得到 Mono<Mono<U>>（嵌套，没人要）
        Mono.just("user_123")
                .flatMap(id -> fetchUserName(id))  // String → Mono<String>
                .subscribe(name -> System.out.println("  flatMap 结果: " + name));

        // 对比：如果错用 map 返回 Mono
        Mono.just("user_123")
                .map(id -> fetchUserName(id))       // 返回 Mono<Mono<String>>
                .subscribe(mono -> System.out.println("  map(错) 结果: " + mono));  // 打印的是 Mono 的 toString

        System.out.println("  ↑ 用 map 得到 Mono<Mono>，用 flatMap 得到 Mono<String>");
    }

    // 模拟异步查询
    private static Mono<String> fetchUserName(String id) {
        return Mono.just("姓名_" + id).delayElement(Duration.ofMillis(50));
    }

    // ════════════════════════════════════════════════════════════
    // 03 · filter — 条件不满足变 empty
    // ════════════════════════════════════════════════════════════
    static void demo03_filter() {
        sep("03 · filter + switchIfEmpty");

        // filter 返回 false → 变成 Mono.empty()
        // 这跟 Flux.filter 不同：Flux 只是跳过该元素，Mono 直接整条链路终止

        Mono.just(5)
                .filter(n -> n > 10)   // 5 > 10 = false → empty
                .switchIfEmpty(Mono.just(-1))  // empty 时给默认值
                .subscribe(n -> System.out.println("  filter(5>10) → " + n));

        Mono.just(15)
                .filter(n -> n > 10)   // 15 > 10 = true → 继续
                .switchIfEmpty(Mono.just(-1))  // 不会执行
                .subscribe(n -> System.out.println("  filter(15>10) → " + n));

        // AgentScope 场景: 检查 AgentState 是否存在
        Mono.just("session-001")
                .filter(id -> id.startsWith("active-"))  // 不符合
                .switchIfEmpty(Mono.error(new IllegalStateException("无效的 sessionId")))
                .subscribe(
                        v -> System.out.println("  session ok: " + v),
                        e -> System.out.println("  错误: " + e.getMessage())
                );
    }

    // ════════════════════════════════════════════════════════════
    // 04 · then — 链式编排，忽略前一步的结果
    // ════════════════════════════════════════════════════════════
    static void demo04_then() {
        sep("04 · then / thenReturn / thenEmpty");

        // then(): 忽略上一步结果，返回 Mono<Void>（只关心完成信号）
        Mono.just("step1_done")
                .doOnNext(s -> System.out.println("  执行: " + s))
                .then()  // 结果被丢弃
                .subscribe(null, null,
                        () -> System.out.println("  then() → 只收到完成信号"));

        // thenReturn: 忽略上一步结果，返回固定值
        Mono.just("check_db")
                .doOnNext(s -> System.out.println("  执行: " + s))
                .thenReturn("OK")  // 不管查到什么，固定返回 "OK"
                .subscribe(v -> System.out.println("  thenReturn → " + v));

        // AgentScope 场景: 先保存状态，再返回消息
        // saveStateToSession() 返回 Mono<Void>
        // 你只关心"保存完"，不关心 Void 里的 null
        Mono.fromRunnable(() -> System.out.println("  [保存状态到 Session...]"))
                .then(Mono.just("回复消息内容"))  // 保存完 → 返回消息
                .subscribe(reply -> System.out.println("  [最终返回] " + reply));
    }

    // ════════════════════════════════════════════════════════════
    // 05 · zip / zipWith — 等两个 Mono 都完成后合并
    // ════════════════════════════════════════════════════════════
    static void demo05_zip() {
        sep("05 · zip / zipWith");

        // 两个独立的异步调用，等两个都完成
        Mono<String> userName = fetchUserName("user_1");
        Mono<Integer> userAge = Mono.just(25).delayElement(Duration.ofMillis(30));

        // zip: 两个都完成 → Tuple2
        Mono.zip(userName, userAge)
                .subscribe(tuple -> System.out.println(
                        "  zip: " + tuple.getT1() + ", " + tuple.getT2() + " 岁"));

        // zipWith: 只有两个 Mono 时的链式写法
        userName.zipWith(userAge)
                .subscribe(tuple -> System.out.println(
                        "  zipWith: " + tuple.getT1() + ", " + tuple.getT2() + " 岁"));

        // 当 2+ 个 Mono，用 zip + BiFunction 直接处理
        Mono.zip(userName, userAge)
                .map(tuple -> tuple.getT1() + " (" + tuple.getT2() + "岁)")
                .subscribe(s -> System.out.println("  zip+map: " + s));
    }

    // ════════════════════════════════════════════════════════════
    // 06 · 错误处理 — 3 种恢复策略 + empty 兜底
    // ════════════════════════════════════════════════════════════
    static void demo06_error() {
        sep("06 · 错误处理");

        // 1. onErrorReturn: 静默返回固定值
        riskyOperation()
                .onErrorReturn("降级值")
                .subscribe(v -> System.out.println("  onErrorReturn → " + v));

        // 2. onErrorResume: 根据异常类型选择不同的降级路径
        riskyOperation()
                .onErrorResume(RuntimeException.class, e -> Mono.just("运行时异常捕获"))
                .onErrorResume(IllegalStateException.class, e -> Mono.just("状态异常捕获"))
                .subscribe(v -> System.out.println("  onErrorResume → " + v));

        // 3. defaultIfEmpty: 不是异常，但 empty 时给默认值
        Mono.empty()
                .defaultIfEmpty("没有数据")
                .subscribe(v -> System.out.println("  defaultIfEmpty → " + v));

        // 4. retry: 失败后重试 N 次（AgentScope: ModelConfig.maxRetries=3）
        //    注意: retry 只对 subscribe 时发生的异常生效
        Mono.error(new RuntimeException("临时故障"))
                .retry(2)  // 重试 2 次，还是失败就把异常抛给下游
                .onErrorReturn("重试后仍失败，最终降级")
                .subscribe(v -> System.out.println("  retry(2) → " + v));

        // 5. timeout: 超时后抛出 TimeoutException
        Mono.just("ok")
                .delayElement(Duration.ofMillis(200))
                .timeout(Duration.ofMillis(50))  // 50ms 等不到就超时
                .onErrorReturn("超时了")
                .subscribe(v -> System.out.println("  timeout → " + v));
    }

    private static Mono<String> riskyOperation() {
        return Mono.error(new RuntimeException("真实错误信息"));
    }

    // ════════════════════════════════════════════════════════════
    // 07 · block() — 什么时候用，什么时候不该用
    // ════════════════════════════════════════════════════════════
    static void demo07_block() {
        sep("07 · block() 的正确用法");

        // ✅ 正确: main 方法 / 测试里，需要等结果
        String result = Mono.just("hello")
                .map(String::toUpperCase)
                .block();
        System.out.println("  block 同步结果: " + result);

        // ✅ 正确: blockOptional() — 允许 empty 不抛异常
        Optional<String> maybe = Mono.<String>empty().blockOptional();
        System.out.println("  blockOptional(empty): " + maybe.orElse("无值"));

        // ❌ 错误: 在反应式 Controller 里 block
        // Spring WebFlux Controller 返回 Mono 时不要 block——
        // 你 block 了，反应式线程池就浪费了
        // 框架会自动 subscribe 并写出 HTTP 响应

        // ❌ 错误: 在 flatMap / map 里 block
        // Mono.just("input")
        //     .map(s -> fetchUserName(s).block())  // 死锁/性能问题
        // 正确做法是用 flatMap

        // ✅ AgentScope main 里的典型模式
        // agent.call(msg, ctx).block() — 同步等 Agent 完整回复
        System.out.println("  AgentScope 典型: Msg reply = agent.call(...).block();");
    }

    // ════════════════════════════════════════════════════════════
    // 08 · defer — 延迟创建，每次 subscribe 都重新执行
    // ════════════════════════════════════════════════════════════
    static void demo08_defer() {
        sep("08 · defer 延迟创建");

        // 问题：Mono.just() 在声明时就执行了参数求值
        // 所以下面的时间是"声明时"的时间，多次 subscribe 不会变
        Mono<Long> eager = Mono.just(System.currentTimeMillis());
        eager.subscribe(t -> System.out.println("  just(第1次): " + t));
        sleep(10);
        eager.subscribe(t -> System.out.println("  just(第2次): " + t + " ← 没变!"));

        // defer: 每次 subscribe 才调用 lambda，重新生成
        Mono<Long> lazy = Mono.defer(() -> Mono.just(System.currentTimeMillis()));
        lazy.subscribe(t -> System.out.println("  defer(第1次): " + t));
        sleep(10);
        lazy.subscribe(t -> System.out.println("  defer(第2次): " + t + " ← 变了!"));

        System.out.println("  AgentScope 里 retry/resume 要配合 defer，" +
                "否则重试拿到的还是旧结果");
    }

    // ════════════════════════════════════════════════════════════
    // 09 · using — 资源获取/业务/释放的三段式保证
    // ════════════════════════════════════════════════════════════
    static void demo09_using() {
        sep("09 · Mono.using（AgentBase.call 的实现基础）");

        // AgentBase.call() 就是这个模式:
        // Mono.using(
        //     () -> acquireExecution(),    // 1. 获取资源(抢锁)
        //     (agent) -> doCall(msg),      // 2. 用资源做事
        //     (agent) -> releaseExecution(), // 3. 一定释放
        //     true                          // 4. 提前清理
        // )

        // 模拟一个数据库连接场景
        Mono<String> result = Mono.using(
                // 参数1: 获取资源 — subscribe 时执行
                () -> {
                    System.out.println("  1. 获取数据库连接");
                    return "DB_Connection_#42";
                },

                // 参数2: 用资源做事 — 返回 Mono<T>
                conn -> {
                    System.out.println("  2. 用 " + conn + " 执行查询");
                    return Mono.just("查询结果: Alice, 25岁");
                },

                // 参数3: 清理资源 — 无论如何都会执行
                conn -> {
                    System.out.println("  3. 释放 " + conn);
                },

                // 参数4: eager=true — 业务结束立即清理
                true
        );

        String data = result.block();
        System.out.println("  最终获得: " + data);

        // 异常场景也能保证清理
        System.out.println();
        Mono.using(
                () -> "conn",
                conn -> Mono.error(new RuntimeException("查询失败")),
                conn -> System.out.println("  清理: " + conn + "（尽管报错了）"),
                true
        ).onErrorReturn("降级数据").subscribe(v -> System.out.println("  获得: " + v));

        System.out.println("  ↑ 这就是 acquireExecution / releaseExecution 的保证机制");
    }

    // ════════════════════════════════════════════════════════════
    // 10 · AgentScope 一次 call() 的 Mono 链 完整模拟
    // ════════════════════════════════════════════════════════════
    static void demo10_agent_flow() {
        sep("10 · AgentScope call() 的完整 Mono 链");

        // 这是一次 agent.call() 内部 Mono 链的简化模拟:
        //   前置Hook → 推理 → 执行工具 → 推理 → 保存状态 → 返回回复
        //
        // 在 AgentScope 源码中，每一步都是 flatMap 串起来的:
        //   checkInterrupted()
        //     .then(firePreReasoning())
        //     .flatMap(ctx -> reasoning())
        //     .flatMap(msg -> firePostReasoning(msg))
        //     .flatMap(msg -> acting(msg))
        //     .flatMap(msg -> executeIteration(next))
        //     ...
        //     .flatMap(result -> saveStateToSession().thenReturn(result))

        Mono<String> fullFlow = checkInterrupted()                // 1. 检查中断
                .then(firePreReasoning())                        // 2. 触发 Pre hook
                .then(reasoning("用户: 今天天气怎么样?"))          // 3. 调用 LLM
                .flatMap(reply -> firePostReasoning(reply))       // 4. 触发 Post hook
                .flatMap(reply -> {
                    if (reply.contains("tool:")) {
                        return acting(reply);                    // 5. 如需执行工具
                    }
                    return Mono.just(reply);
                })
                .flatMap(reply -> saveState()                    // 6. 保存状态
                        .thenReturn("[最终回复] " + reply));

        // 整个链路被 block() 触发执行
        String finalReply = fullFlow.block();
        System.out.println(finalReply);

        System.out.println();
        System.out.println("  ↑ 每一步用 flatMap 串: 每一步都是异步的，" +
                "但顺序是保证的");
        System.out.println("  ↑ then: 只关心前一步完成信号，不关心它的值");
        System.out.println("  ↑ flatMap: 需要前一步的结果来决定下一步做什么");
    }

    // ── Agent 流程模拟方法 ──

    private static Mono<Void> checkInterrupted() {
        System.out.println("  [1] checkInterrupted → 通过");
        return Mono.empty();  // Void 信号，表示"可以继续"
    }

    private static Mono<Void> firePreReasoning() {
        System.out.println("  [2] firePreReasoning → 旧版 Hook 调度");
        return Mono.fromRunnable(() -> { /* 触发旧版 PreReasoning Hook */ });
    }

    private static Mono<String> reasoning(String userInput) {
        System.out.println("  [3] reasoning → 调用 Model.stream() → 组装 Msg");
        return Mono.just("今天天气晴朗, 温度25°C。tool: 无");
    }

    private static Mono<String> firePostReasoning(String reply) {
        System.out.println("  [4] firePostReasoning → 旧版 Hook 后处理");
        return Mono.just(reply);
    }

    private static Mono<String> acting(String reply) {
        System.out.println("  [5] acting → 执行工具调用");
        // 模拟工具执行: 提取 toolUse, 权限检查, 执行, 收集结果
        return Mono.just(reply + " [工具已执行]");
    }

    private static Mono<Void> saveState() {
        System.out.println("  [6] saveState → 写 AgentState 到 Session");
        return Mono.fromRunnable(() -> { /* state.sessions.xxx.json 写入磁盘 */ });
    }

    // ── 工具方法 ──

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void sep(String title) {
        System.out.println();
        System.out.println("─".repeat(50));
        System.out.println("  " + title);
        System.out.println("─".repeat(50));
    }
}
