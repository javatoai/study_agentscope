package com.javatoai.agentscope.homework.weather;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.List;

/**
 *
 */
public class WeatherTool {

    @Tool(
            name = "get_weather",
            description = "查询某城市的天气情况",
            concurrencySafe = true,
            readOnly = true
    )
    public String getWeather(@ToolParam(name = "city", required = true, description = "城市名称") String city){
        switch (city){
            case "北京" -> {
                return "天气晴，温度15度，湿度30%，风力2级";
            }
            case "上海" ->{
                return "天气阴，伴随小雨，温度10度，湿度90%，风力3级";
            }
            case null -> {
                return "您没有输入城市地址，请重新输入";
            }
            default -> {
                return "不支持该城市的天气查询: " + city;
            }
        }
    }

    @Tool(
            name = "get_support_city_for_weather",
            description = "查询哪些城市支持天气查询",
            readOnly = true
    )
    public List<String> getSupportCityForWeather(){
        return List.of("北京", "上海");
    }


}
