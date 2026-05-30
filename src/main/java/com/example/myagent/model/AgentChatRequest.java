package com.example.myagent.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 编程助手对话请求。
 *
 * <p>projectPath 可以为空，默认使用 application.yaml 中的 agent.workspace-root。
 * 如果传入路径，服务会校验它必须在 workspace-root 目录下。</p>
 */
public class AgentChatRequest {

    private String sessionId;

    private String projectPath;

    @NotBlank(message = "question 不能为空")
    private String question;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
