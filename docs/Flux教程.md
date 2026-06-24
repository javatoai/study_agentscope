# Project Reactor Flux / Mono 教程

> 配合 `flux_study/FluxStudy.java` 可运行示例学习。

## 1. 什么是 Flux / Mono

Flux 和 Mono 是 **Project Reactor** 的核心类型，实现[响应式流 (Reactive Streams)](https://www.reactive-streams.org/) 规范。

| 类型 | 元素数量 | 类比 |
|------|---------|------|
| `Mono<T>` | 0 或 1 | `CompletableFuture<T>` |
| `Flux<T>` | 0 到无限 | `Stream<T>`（但异步、可回压） |

---

## 2. 四个核心思想

### 2.1 推 (Push) vs 拉 (Pull)

传统代码是**拉**：你主动去取数据。响应式是**推**：数据来了主动通知你。

```java
// 传统：主动拉取
List<String> list = db.query();
for (String row : list) { process(row); }

// 响应式：被动接收，数据推给你
Flux<String> flux = db.queryReactive();
flux.doOnNext(row -> process(row)).subscribe();
```

### 2.2 惰性 (Lazy)

一整条链只是**声明计划**，`subscribe()` 才是启动开关。同样一个 Flux 可以多次订阅，每次重新执行。

```java
Flux<String> blueprint = Flux.just("hello", "world")
        .map(s -> { System.out.println("map: " + s); return s.toUpperCase(); });
// 什么都没打印——还没 subscribe

blueprint.subscribe();  // 第一轮
blueprint.subscribe();  // 第二轮，重新跑一次
```

### 2.3 声明式 (Declarative)

控制流 (if / for / try) 被抽象成操作符 (filter / flatMap / onErrorResume)，代码是一条直管子。

```java
flux.filter(s -> s.length() > 3)     // 过滤
    .map(String::toUpperCase)        // 变换
    .doOnNext(System.out::println)   // 旁路打印
    .onErrorResume(e -> fallback())  // 出错兜底
    .subscribe();
```

### 2.4 回压 (Backpressure)

数据可能来得比消费更快。订阅者可以告诉上游"慢点发"，防止内存被淹没。AgentScope 的 `streamEvents()` 正是用这个机制协调 LLM 产 token 速率与终端消费速率。

---

## 3. 创建 Flux / Mono

```java
// 固定元素
Flux.just("a", "b", "c");
Mono.just("done");

// 从集合
Flux.fromIterable(List.of(1, 2, 3));

// 连续整数
Flux.range(5, 4);  // 5, 6, 7, 8

// 立即完成、不发元素
Flux.empty();
Mono.empty();

// 异常
Flux.error(new RuntimeException("boom"));
```

---

## 4. 核心操作符

### 4.1 map — 同步一对一转换

```java
Flux.just("hello", "world")
    .map(String::toUpperCase);  // "HELLO", "WORLD"
```

### 4.2 flatMap — 异步一对多展平

先映射为子 Flux/Mono，再展平汇入主流程。

```java
Flux.just("AgentScope", "Java")
    .flatMap(word -> Flux.just(word.split("")));
// "A","g","e","n","t","S","c","o","p","e","J","a","v","a"
```

### 4.3 filter — 过滤

```java
Flux.range(1, 10)
    .filter(n -> n % 2 == 0);  // 2, 4, 6, 8, 10
```

### 4.4 doOnXxx — 旁路观测（不改数据）

Middleware 的核心机制。

| 方法 | 触发时机 |
|------|---------|
| `doOnSubscribe` | 被订阅时 |
| `doOnNext` | 每个元素经过时 |
| `doOnComplete` | 正常结束时 |
| `doOnError` | 异常发生时 |
| `doFinally` | 结束/异常/取消都触发 |

```java
Flux.just("step1", "step2")
    .doOnSubscribe(s -> log("开始"))
    .doOnNext(data -> log("处理: " + data))
    .doOnComplete(() -> log("完成"))
    .doFinally(signal -> log("最终信号: " + signal))
    .subscribe();
```

### 4.5 merge — 并发合并

多个 Flux 同时运行，谁先产生数据谁先输出。

```java
Flux<String> f1 = Flux.just("A1", "A2").delayElements(Duration.ofMillis(10));
Flux<String> f2 = Flux.just("B1", "B2").delayElements(Duration.ofMillis(5));
Flux.merge(f1, f2);  // B1, A1, B2, A2（B 先到因为 delay 更短）
```

### 4.6 zip — 对齐合并

两个 Flux 一一配对。

```java
Flux<String> names = Flux.just("Alice", "Bob");
Flux<Integer> scores = Flux.just(95, 87);
Flux.zip(names, scores)
    .map(t -> t.getT1() + " → " + t.getT2() + "分");
// "Alice → 95分", "Bob → 87分"
```

---

## 5. 错误处理

```java
flux.onErrorResume(e -> {
    log("捕获: " + e.getMessage());
    return Flux.just("FALLBACK");  // 降级到兜底值
});
```

| 方法 | 行为 |
|------|------|
| `onErrorReturn(v)` | 异常时直接返回固定值 |
| `onErrorResume(fn)` | 异常时切换到另一个 Flux |
| `onErrorContinue(fn)` | 忽略异常，继续处理后续元素 |

---

## 6. block() vs subscribe()

| | `block()` | `subscribe()` |
|---|---|---|
| 阻塞当前线程 | 是 | 否 |
| 能拿到返回值 | 能，返回 `T` | 不能，返回 `Disposable` |
| 适用场景 | CLI / main 方法 / 测试 | Web 服务 / 事件驱动 |

```java
// CLI 程序——必须 block，否则 main 退出回调来不及执行
Msg reply = agent.call(msgs, context).block();

// Web 服务——返回 Mono，框架替你 subscribe
@GetMapping("/chat")
public Mono<Msg> chat(@RequestParam String msg) {
    return agent.call(List.of(new UserMessage(msg)), context);
}
```

---

## 7. 在 AgentScope 中的使用场景

### 7.1 streamEvents() — 流式事件

```java
// Flux<AgentEvent> — LLM 的每个 token 作为一个事件推给你
agent.streamEvents(question)
    .doOnNext(event -> {
        switch (event.getType()) {
            case TEXT_BLOCK_DELTA  -> System.out.print(((TextBlockDeltaEvent) event).getDelta());
            case TOOL_CALL_START   -> log("工具: " + ((ToolCallStartEvent) event).getToolCallName());
            case TOOL_RESULT_END   -> log("完成: " + ((ToolResultEndEvent) event).getState());
        }
    })
    .blockLast();  // 等待流结束
```

### 7.2 call() — 同步获取回复

```java
// Mono<Msg> — 一个调用返回一个回复
Msg reply = agent.call(msgs, context).block();
```

### 7.3 Middleware 中的洋葱模式

```java
public Flux<AgentEvent> onModelCall(Agent agent, ModelCallInput input,
                                     Function<ModelCallInput, Flux<AgentEvent>> next) {
    long start = System.nanoTime();
    return next.apply(input)               // 执行内层
        .doFinally(signal -> log("耗时: " + (System.nanoTime() - start) / 1_000_000 + "ms"));
}
```

---

## 8. 操作符速查表

| 操作符 | 作用 | 模式 |
|--------|------|------|
| `map` | 同步 1:1 变换 | `A → B` |
| `flatMap` | 异步 1:N 展平 | `A → Flux<B>` |
| `filter` | 按条件过滤 | `A → 丢弃/保留` |
| `doOnNext` | 旁路观测（不改数据） | 偷看一眼 |
| `doOnComplete` | 流完成时的回调 | 收尾工作 |
| `doFinally` | 无论如何都会执行 | 资源清理 |
| `merge` | 多 Flux 并发合并 | 多合一 |
| `zip` | 多 Flux 对齐配对 | 一对一组装 |
| `onErrorResume` | 异常降级 | 出错兜底 |
| `block` | 同步阻塞等结果 | 停在当前线程 |
| `blockLast` | 同步阻塞等 Flux 发完 | 停在当前线程 |
| `subscribe` | 异步启动 | 不阻塞 |

---

## 9. 运行学习示例

```powershell
mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.flux_study.FluxStudy
```

在 `main` 方法中注释/取消注释 `demoXX` 方法来逐个运行 10 个示例：

| # | 示例 | 学会什么 |
|---|------|---------|
| 01 | 创建 Flux | `just / fromIterable / range / empty` |
| 02 | map | 同步一对一转换 |
| 03 | flatMap | 异步展平 |
| 04 | filter | 过滤 |
| 05 | doOnXxx | 旁路观测（Middleware 核心） |
| 06 | merge | 并发合并 |
| 07 | zip | 对齐合并 |
| 08 | block vs subscribe | 同步 vs 异步 |
| 09 | 错误处理 | onErrorResume 降级 |
| 10 | streamEvents 模拟 | 事件流分发处理 |
