package com.example.myagent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 编程助手对话响应。
 *
 * <p>usedFiles 会返回本次回答实际送入大模型的代码文件，方便你判断答案依据是否可靠。</p>
 */
public class AgentChatResponse {

    private String sessionId;

    private String answer;

    private List<String> usedFiles = new ArrayList<String>();

    private String projectRoot;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getUsedFiles() {
        return usedFiles;
    }

    public void setUsedFiles(List<String> usedFiles) {
        this.usedFiles = usedFiles;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }
}
