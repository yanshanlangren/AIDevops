package com.example.aidevops.task;

public class TaskError {
    private final String type;
    private final String message;

    public TaskError(String type, String message) {
        this.type = type;
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}
