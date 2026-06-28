package com.javatoai.agentscope.homework.todo;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.*;

/**
 *
 */
public class TodoTool {
    enum TodoState {
        INIT,
        DOING,
        DONE
    }
    record Todo(String id, int idx, TodoState todoState, String todoTxt){
        Todo changeState(TodoState todoState){
            return new Todo(id, idx, todoState, todoTxt);
        }
    }

    private List<Todo> taskList = new ArrayList<>();
    private int todoIdx = 0;

    @Tool(name = "add_todo", description = "增加todo任务")
    public String addTodo(@ToolParam(name = "todo_txt", description = "需要做的任务内容") String todoTxt){
        taskList.add(new Todo(UUID.randomUUID().toString().substring(0, 5), todoIdx++, TodoState.INIT, todoTxt));
        return "success";
    }

    @Tool(name = "list_todo", description = "列出所有todo任务")
    public List<Todo> listTodo(){
        return taskList.stream().sorted(Comparator.comparingInt(a -> a.idx)).toList();
    }
    @Tool(name = "list_todo_state", description = "列出所有可用的任务状态")
    public List<TodoState> listTodoState(){
        return Arrays.stream(TodoState.values()).toList();
    }

    @Tool(name = "change_todo_state", description = "改变任务状态")
    public String changeTodoState(@ToolParam(name = "idx", description = "todo任务序号") int idx, @ToolParam(name = "todo_state", description = "目标todo任务状态") TodoState todoState){
        for (int i = 0; i < taskList.size(); i++) {
            if(taskList.get(i).idx == idx){
                taskList.set(i, taskList.get(i).changeState(todoState));
                return "success";
            }
        }
        return "找不到任务序号:" + idx;
    }


    public static void main(String[] args) {

    }
}
