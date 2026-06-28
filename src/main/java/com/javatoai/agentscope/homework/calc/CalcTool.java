package com.javatoai.agentscope.homework.calc;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.math.BigDecimal;

/**
 *
 */
public class CalcTool {
    @Tool(name = "calc", description = "对数字进行加减乘除运算，并得到运算结果", readOnly = true)
    public String calc(@ToolParam(name = "a", description = "数字a") String a,
                        @ToolParam(name = "b", description = "数字b") String b,
                        @ToolParam(name = "operate", description = "运算符：+-*/，分别代表加减乘除") String operate){
        try {
            BigDecimal aValue = new BigDecimal(a);
            BigDecimal bValue = new BigDecimal(b);
            switch (operate){
                case "+": {
                    return aValue.add(bValue).toString();
                }
                case "-":{
                    return aValue.subtract(bValue).toString();
                }
                case "*", "×":{
                    return aValue.multiply(bValue).toString();
                }
                case "/", "\\":{
                    return aValue.divide(bValue).toString();
                }
                default:{
                    return "unSupport operate: " + operate;
                }
            }
        }catch (Exception e){
            return e.getMessage();
        }
    }
}
