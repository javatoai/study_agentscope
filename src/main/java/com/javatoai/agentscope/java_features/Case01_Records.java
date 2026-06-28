package com.javatoai.agentscope.java_features;

/**
 * <h2>Records 学习案例</h2>
 *
 * <pre>
 *   Record 是 Java 14 预览、Java 16 正式的特性。
 *   一句话：不可变数据的透明载体，编译器自动生成构造器/accessor/equals/hashCode/toString。
 * </pre>
 *
 * <h3>核心特性</h3>
 * <ul>
 *   <li>所有字段自动 {@code private final}</li>
 *   <li>自动生成：全参构造器、字段访问方法（无 get 前缀）、equals/hashCode/toString</li>
 *   <li>不能继承其他类（隐含 extends Record），但可实现接口</li>
 *   <li>可以定义紧凑构造器（compact constructor）做参数校验</li>
 *   <li>可以定义实例方法</li>
 *   <li>可以用 {@code with} 风格创建副本（需手写）</li>
 * </ul>
 */
public final class Case01_Records {

    // ═══════════════════════════════════════════════════════════════
    // 1. 最简 Record —— 一行定义不可变数据
    // ═══════════════════════════════════════════════════════════════

    /** 传统写法（~40 行样板代码） */
    static class TraditionalTodo {
        private final String id;
        private final String text;
        private final boolean done;

        public TraditionalTodo(String id, String text, boolean done) {
            this.id = id;
            this.text = text;
            this.done = done;
        }

        public String id()  { return id; }
        public String text() { return text; }
        public boolean done() { return done; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof TraditionalTodo that)) return false;
            return id.equals(that.id) && text.equals(that.text) && done == that.done;
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, text, done); }
        @Override public String toString() {
            return "TraditionalTodo[id=" + id + ", text=" + text + ", done=" + done + "]";
        }
    }

    /** Record 写法 —— 一行搞定上面 30 行 */
    record TodoRecord(String id, String text, boolean done) {}

    // ═══════════════════════════════════════════════════════════════
    // 2. 紧凑构造器 —— 参数校验
    // ═══════════════════════════════════════════════════════════════

    record PositiveNumber(int value) {
        PositiveNumber {
            if (value < 0) {
                throw new IllegalArgumentException("value 必须 >= 0, 实际: " + value);
            }
            // 不需要写 this.value = value，编译器自动赋值
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Record 实现接口 + 实例方法
    // ═══════════════════════════════════════════════════════════════

    interface ToolCall {
        String name();
        String result();
    }

    record SimpleToolCall(String name, String result) implements ToolCall {
        /** 实例方法：可以访问字段 */
        String summary() {
            return name + " → " + (result.length() > 20 ? result.substring(0, 20) + "..." : result);
        }

        /** 静态工厂 */
        static SimpleToolCall empty(String name) {
            return new SimpleToolCall(name, "(无结果)");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. 真实场景 —— AgentScope 中的 ActingInput 就是 record
    // ═══════════════════════════════════════════════════════════════

    /**
     * 模仿 AgentScope 的 ActingInput —— 一次工具调用请求
     * (实际源码: ActingInput(List<ToolUseBlock> toolCalls))
     */
    record ToolRequest(String toolName, String parameters) {}

    /**
     * 模仿回包
     */
    record ToolResponse(String toolName, String output, boolean success) {
        /** 紧凑构造器：默认输出截断 */
        ToolResponse {
            if (output.length() > 50) {
                output = output.substring(0, 50) + "...(截断)";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // main
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("=".repeat(50));
        System.out.println("  1. 基本使用");
        System.out.println("=".repeat(50));

        TodoRecord t = new TodoRecord("t1", "学 Java Record", false);
        System.out.println("  id   = " + t.id());          // 访问器叫 id() 不是 getId()
        System.out.println("  text = " + t.text());
        System.out.println("  done = " + t.done());
        System.out.println("  toString = " + t);            // 自动格式化

        // 相等比较 —— 自动按字段比较
        TodoRecord t2 = new TodoRecord("t1", "学 Java Record", false);
        System.out.println("  t.equals(t2) = " + t.equals(t2));  // true

        System.out.println("\n" + "=".repeat(50));
        System.out.println("  2. 紧凑构造器校验");
        System.out.println("=".repeat(50));

        try {
            new PositiveNumber(-5);
        } catch (IllegalArgumentException e) {
            System.out.println("  校验异常: " + e.getMessage());
        }
        PositiveNumber p = new PositiveNumber(42);
        System.out.println("  PositiveNumber(42) = " + p);

        System.out.println("\n" + "=".repeat(50));
        System.out.println("  3. 实现接口 + 实例方法");
        System.out.println("=".repeat(50));

        SimpleToolCall tc = new SimpleToolCall("get_weather", "北京今天天晴，温度20度，湿度适中");
        System.out.println("  " + tc.summary());
        System.out.println("  name()  = " + tc.name());     // 接口方法
        System.out.println("  result()= " + tc.result());    // 接口方法
        SimpleToolCall empty = SimpleToolCall.empty("noop");
        System.out.println("  " + empty.summary());

        System.out.println("\n" + "=".repeat(50));
        System.out.println("  4. 嵌套 Record（模仿 AgentScope 模式）");
        System.out.println("=".repeat(50));

        ToolRequest req = new ToolRequest("get_weather", "{\"city\":\"北京\"}");
        ToolResponse resp = new ToolResponse("get_weather",
                "北京今天天晴，温度20度，湿度适中，风力2级，适合出行", true);
        System.out.println("  req  = " + req);
        System.out.println("  resp = " + resp);

        System.out.println("\n>>> Case01_Records 完成");
    }
}
