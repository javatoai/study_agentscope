package com.javatoai.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lesson 04 使用的自定义工具类。
 *
 * <p>通过 {@code @Tool} 注解声明工具名、描述与参数 Schema，
 * 再由 {@link io.agentscope.core.tool.Toolkit#registerTool(Object)} 注册到 Agent。
 */
public final class TimeTools {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 查询指定 IANA 时区的当前时间。
     *
     * @param timezone IANA 时区 ID，例如 {@code Asia/Shanghai}
     * @return 格式化后的本地时间字符串
     */
    @Tool(
            name = "get_current_time",
            description = "Returns the current time in a given IANA timezone.",
            readOnly = true,
            concurrencySafe = true)
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "IANA timezone, e.g. Asia/Shanghai")
            String timezone) {
        ZoneId zoneId = ZoneId.of(timezone);
        return LocalDateTime.now(zoneId).format(FORMATTER);
    }
}
