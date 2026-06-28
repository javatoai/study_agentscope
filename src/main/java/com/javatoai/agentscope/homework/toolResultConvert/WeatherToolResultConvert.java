package com.javatoai.agentscope.homework.toolResultConvert;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.ToolResultConverter;

import java.lang.reflect.Type;

/**
 *
 */
public class WeatherToolResultConvert implements ToolResultConverter {
    @Override
    public ToolResultBlock convert(Object result, Type returnType) {
        String string = result.toString();
        if(string.length() > 10){
            string = string.substring(0, 10) + "(长度超过10，已截断)";
        }
        return ToolResultBlock.text(string);
//        return ToolResultBlock.builder()
//                .output(TextBlock.builder().text(string).build())
//                .state(ToolResultState.SUCCESS)
//                .build();
    }
}
