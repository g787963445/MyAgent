package com.example.myagent.controller;

import java.time.LocalDateTime;

/**
 * 统一错误响应结构，方便调用方展示或排查问题。
 */
public class ApiError {

    private LocalDateTime timestamp = LocalDateTime.now();

    private int status;

    private String error;

    private String message;

    public ApiError(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }
}
