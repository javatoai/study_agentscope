package com.javatoai.agentscope.java_features;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * <h2>平台线程 vs 虚拟线程 学习案例</h2>
 *
 * <pre>
 *   核心：
 *   - 平台线程 = OS 线程 1:1，~1MB 栈，昂贵
 *   - 虚拟线程 = JVM 调度，~几百字节，几百万随便开
 *   - 虚拟线程阻塞 = "挂起"让位，不占用载体线程
 *   - 平台线程阻塞 = 真阻塞，载体什么都干不了
 * </pre>
 */
public final class Case04_VirtualThreads {

    // ═══════════════════════════════════════════════════════════════
    // 1. 创建方式对比
    // ═══════════════════════════════════════════════════════════════

    static void demo1_creation() {
        System.out.println("=".repeat(50));
        System.out.println("  1. 创建方式");
        System.out.println("=".repeat(50));

        // 平台线程 —— 通过 Thread 或 ExecutorService
        Thread platform = new Thread(() ->
                System.out.println("  平台线程: " + Thread.currentThread()),
                "platform-1");
        platform.start();

        // 虚拟线程 —— 创建方式和平台线程几乎一样
        // 方式一：Thread.startVirtualThread
        Thread vt1 = Thread.startVirtualThread(() ->
                System.out.println("  虚拟线程(方式1): " + Thread.currentThread()));

        // 方式二：Thread.ofVirtual().start()
        Thread vt2 = Thread.ofVirtual()
                .name("vt-2")
                .start(() -> System.out.println("  虚拟线程(方式2): " + Thread.currentThread()));

        // 方式三：虚拟线程 Executor —— 每个任务一个新虚线程，无需池！
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() ->
                    System.out.println("  虚拟线程(方式3): " + Thread.currentThread()));
        }

        // 等着它们跑完
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        System.out.println("  ✅ 创建方式基本一致，API 几乎无迁移成本");
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. 数量对比 —— 虚拟线程可以开多少？
    // ═══════════════════════════════════════════════════════════════

    static void demo2_scale() throws InterruptedException {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("  2. 数量对比");
        System.out.println("=".repeat(50));

        // 平台线程 —— 试试开 10000 个
        // 大多数机器开到几千就会 OOM 或系统卡死
        System.out.println("  平台线程: 尝试创建 2000 个...");
        ExecutorService platformPool = Executors.newCachedThreadPool();
        AtomicInteger platformCount = new AtomicInteger(0);
        try {
            for (int i = 0; i < 2000; i++) {
                platformPool.submit(() -> {
                    platformCount.incrementAndGet();
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                });
            }
            platformPool.shutdown();
            platformPool.awaitTermination(2, TimeUnit.SECONDS);
            System.out.println("  平台线程创建成功: " + platformCount.get() + " 个");
        } catch (OutOfMemoryError e) {
            System.out.println("  平台线程 OOM! 仅创建了: " + platformCount.get() + " 个");
        }

        // 虚拟线程 —— 轻松开 10 万个
        System.out.println("  虚拟线程: 创建 100,000 个...");
        AtomicInteger vtCount = new AtomicInteger(0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100_000; i++) {
                executor.submit(() -> {
                    vtCount.incrementAndGet();
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                });
            }
        }
        System.out.println("  虚拟线程创建成功: " + vtCount.get() + " 个 ✅");
        System.out.println("  100,000 个虚拟线程 ≈ 与 2,000 个平台线程相当的资源");
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. 阻塞行为对比 —— 核心区别
    // ═══════════════════════════════════════════════════════════════

    static void demo3_blocking() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("  3. 阻塞行为 —— 最关键的区别");
        System.out.println("=".repeat(50));

        System.out.println("  模拟场景: 3 个任务，每个需要等待 100ms，");
        System.out.println("  但只有 2 个载体线程。看吞吐量差异。");

        // 平台线程池 —— 固定 2 个线程
        // 任务1、2 启动，任务3 排队
        // 每个任务 Thread.sleep → 真阻塞，载体线程闲着等
        Instant start1 = Instant.now();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 1; i <= 3; i++) {
                int taskId = i;
                pool.submit(() -> {
                    try {
                        System.out.println("  [平台] 任务" + taskId + " 开始(" + Thread.currentThread().getName() + ")");
                        Thread.sleep(100);  // ← 载体线程真阻塞，什么都不干
                        System.out.println("  [平台] 任务" + taskId + " 完成");
                    } catch (InterruptedException ignored) {}
                });
            }
            // 等待完成
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        Duration d1 = Duration.between(start1, Instant.now());
        System.out.println("  平台线程池(2载体) 3任务 总耗时: " + d1 + " (约 200ms，因为一次只能跑 2 个)");

        // 虚拟线程 —— 无需池，每个任务一个虚线程
        // 3 个虚拟线程都立刻启动，阻塞时自动释放载体
        Instant start2 = Instant.now();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= 3; i++) {
                int taskId = i;
                executor.submit(() -> {
                    try {
                        System.out.println("  [虚拟] 任务" + taskId + " 开始(" + Thread.currentThread().getName() + ")");
                        Thread.sleep(100);  // ← 只"挂起"虚拟线程，不占载体
                        System.out.println("  [虚拟] 任务" + taskId + " 完成");
                    } catch (InterruptedException ignored) {}
                });
            }
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        Duration d2 = Duration.between(start2, Instant.now());
        System.out.println("  虚拟线程 3任务 总耗时: " + d2 + " (约 100ms，三个并发跑完)");
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. IO 密集场景 —— 真实优势
    // ═══════════════════════════════════════════════════════════════

    static void demo4_ioScenario() throws Exception {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("  4. IO 密集场景 —— 模拟 100 个 HTTP 请求（每个 200ms）");
        System.out.println("=".repeat(50));

        int taskCount = 100;

        // 平台线程池(10线程) —— 串行排队
        Instant start1 = Instant.now();
        try (var pool = Executors.newFixedThreadPool(10)) {
            var futures = IntStream.range(0, taskCount)
                    .mapToObj(i -> pool.submit(() -> {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                        return i;
                    }))
                    .toList();
            for (var f : futures) f.get();
        }
        Duration d1 = Duration.between(start1, Instant.now());
        long ms1 = d1.toMillis();
        System.out.printf("  平台线程池(10): %d 任务 → %dms (吞吐量: %.0f req/s)%n",
                taskCount, ms1, taskCount * 1000.0 / ms1);

        // 虚拟线程 —— 全并发
        Instant start2 = Instant.now();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, taskCount)
                    .mapToObj(i -> executor.submit(() -> {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                        return i;
                    }))
                    .toList();
            for (var f : futures) f.get();
        }
        Duration d2 = Duration.between(start2, Instant.now());
        long ms2 = d2.toMillis();
        System.out.printf("  虚拟线程:         %d 任务 → %dms (吞吐量: %.0f req/s)%n",
                taskCount, ms2, taskCount * 1000.0 / ms2);
        System.out.printf("  提速: %.0f 倍%n", (double) ms1 / ms2);
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. ⚠️ 虚拟线程 + synchronized 陷阱
    // ═══════════════════════════════════════════════════════════════

    static Object syncLock = new Object();
    static ReentrantLock reentrantLock = new ReentrantLock();

    static void demo5_synchronizedTrap() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("  5. ⚠️ synchronized 陷阱");
        System.out.println("=".repeat(50));

        System.out.println("  虚拟线程在 synchronized 块里阻塞时，");
        System.out.println("  会 pin 住载体线程(JDK 19-20)，导致其他 VT 无法使用该载体。");

        // synchronized 版本 —— 载体被 pin
        System.out.println("\n  ——— synchronized (会 pin 载体) ———");
        Instant start1 = Instant.now();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= 5; i++) {
                int taskId = i;
                executor.submit(() -> {
                    synchronized (syncLock) {      // ← pin 载体线程
                        try {
                            System.out.println("   [sync] 任务" + taskId + " 进入同步块");
                            Thread.sleep(100);      // ← 载体真阻塞!
                        } catch (InterruptedException ignored) {}
                    }
                    System.out.println("   [sync] 任务" + taskId + " 离开同步块");
                });
            }
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        Duration d1 = Duration.between(start1, Instant.now());
        System.out.println("  synchronized: " + d1.toMillis() + "ms (5个排队，约 500ms)");

        // ReentrantLock 版本 —— 不 pin 载体（JDK 24+ 已解决 synchronized pinning）
        System.out.println("\n  ——— ReentrantLock (不 pin 载体) ———");
        Instant start2 = Instant.now();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= 5; i++) {
                int taskId = i;
                executor.submit(() -> {
                    reentrantLock.lock();
                    try {
                        System.out.println("   [lock] 任务" + taskId + " 进入锁区");
                        Thread.sleep(100);          // ← 虚拟线程被挂起，不占载体
                    } catch (InterruptedException ignored) {} finally {
                        reentrantLock.unlock();
                    }
                    System.out.println("   [lock] 任务" + taskId + " 离开锁区");
                });
            }
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        Duration d2 = Duration.between(start2, Instant.now());
        System.out.println("  ReentrantLock: " + d2.toMillis() + "ms (5个并发，约 100ms)");
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. 虚拟线程也适合 CPU 密集型吗？
    // ═══════════════════════════════════════════════════════════════

    static void demo6_cpuWork() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("  6. CPU 密集型 —— 虚拟线程不占优势");
        System.out.println("=".repeat(50));

        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("  可用 CPU 核心: " + cores);

        // CPU 密集型任务
        Runnable cpuTask = () -> {
            long sum = 0;
            for (int i = 0; i < 10_000_000; i++) {
                sum += i * 3;
            }
        };

        // 平台线程池(core 数量)
        Instant start1 = Instant.now();
        try (var pool = Executors.newFixedThreadPool(cores)) {
            var futures = IntStream.range(0, cores * 2)
                    .mapToObj(i -> pool.submit(cpuTask))
                    .toList();
            for (var f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
        }
        Duration d1 = Duration.between(start1, Instant.now());

        // 虚拟线程
        Instant start2 = Instant.now();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, cores * 2)
                    .mapToObj(i -> executor.submit(cpuTask))
                    .toList();
            for (var f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
        }
        Duration d2 = Duration.between(start2, Instant.now());

        System.out.printf("  平台线程池(%d): %dms%n", cores, d1.toMillis());
        System.out.printf("  虚拟线程:       %dms (差不多，没优势)%n", d2.toMillis());
        System.out.println("  ✅ CPU 密集用平台线程就够了，虚拟线程不加快计算");
    }

    // ═══════════════════════════════════════════════════════════════
    // main
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        System.out.println("  Java 版本: " + System.getProperty("java.version"));
        System.out.println("  可用核心数: " + Runtime.getRuntime().availableProcessors());

        demo1_creation();
        demo2_scale();
        demo3_blocking();
        demo4_ioScenario();
        demo5_synchronizedTrap();
        demo6_cpuWork();

        System.out.println("\n" + "=".repeat(50));
        System.out.println("  总结");
        System.out.println("=".repeat(50));
        System.out.println("""

                  ┌─────────┬──────────────────┬──────────────────┐
                  │         │   平台线程         │   虚拟线程         │
                  ├─────────┼──────────────────┼──────────────────┤
                  │ 资源     │ ~1MB 栈/个        │ ~几百字节/个       │
                  │ 数量     │ <5000             │ 百万级            │
                  │ 阻塞     │ 真阻塞(浪费载体)   │ 挂起(释放载体)     │
                  │ 线程池   │ 必须用             │ 不需要            │
                  │ 适合     │ CPU 密集           │ IO 密集          │
                  │ 锁       │ 随便                 │ 避免 synchronized │
                  └─────────┴──────────────────┴──────────────────┘

                  """);
    }
}
