package com.javatoai.agentscope.homework.weather_with_limit;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.List;

/**
 *
 */
public class WeatherTool {
    @Tool(name = "get_weather", description = "查询指定城市的天气")
    public String getWeather(@ToolParam(name = "city", description = "查询的城市") String city){
        switch (city){
            case "北京" ->{
                return "天气晴朗，温度20度，湿度50%，无风";
            }
            case "上海" ->{
                return "下雨，温度15度，湿度90%，4级风";
            }
            case "江苏" ->{
                return "天气阴，温度20度，湿度10%，无风";
            }
            case "杭州" ->{
                return "天气晴朗，温度19度，湿度80%，3级风";
            }
            case null -> {
                return "请输入城市";
            }
            default -> {
                return "不存在该城市的天气：" + city;
            }
        }
    }


    @Tool(name = "get_city_list", description = "获取支持查询天气的城市列表")
    public List<String> getCityList(){
        return List.of("北京", "江苏", "杭州", "上海");
    }
}
