package com.example.myagent.model;

/**
 * 当前 Agent 允许浏览和读取的工作区范围。
 */
public class WorkspaceResponse {

    private String workspaceRoot;

    public WorkspaceResponse(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }
}
