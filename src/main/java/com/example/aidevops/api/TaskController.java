package com.example.aidevops.api;

import com.example.aidevops.task.IncidentTaskService;
import com.example.aidevops.task.TaskRecord;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
public class TaskController {
    private final IncidentTaskService taskService;

    public TaskController(IncidentTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}")
    public TaskRecord get(@PathVariable String taskId) {
        return taskService.get(taskId);
    }
}
