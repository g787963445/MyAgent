package com.example.myagent.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次问答的本地历史记录。
 */
public class ChatHistoryRecord {

    private LocalDateTime createdAt;

    private String sessionId;

    private String projectRoot;

    private String question;

    private String answer;

    private List<String> usedFiles = new ArrayList<String>();

    public ChatHistoryRecord() {
    }

    public ChatHistoryRecord(LocalDateTime createdAt, String sessionId, String projectRoot,
                             String question, String answer, List<String> usedFiles) {
        this.createdAt = createdAt;
        this.sessionId = sessionId;
        this.projectRoot = projectRoot;
        this.question = question;
        this.answer = answer;
        this.usedFiles = usedFiles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public List<String> getUsedFiles() {
        return usedFiles;
    }
}
