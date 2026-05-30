package com.example.myagent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地目录浏览响应。
 */
public class DirectoryListResponse {

    private String workspaceRoot;

    private String currentPath;

    private String parentPath;

    private List<DirectoryItem> directories = new ArrayList<DirectoryItem>();

    public DirectoryListResponse(String workspaceRoot, String currentPath, String parentPath, List<DirectoryItem> directories) {
        this.workspaceRoot = workspaceRoot;
        this.currentPath = currentPath;
        this.parentPath = parentPath;
        this.directories = directories;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public String getParentPath() {
        return parentPath;
    }

    public List<DirectoryItem> getDirectories() {
        return directories;
    }
}
