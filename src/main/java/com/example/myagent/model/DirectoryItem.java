package com.example.myagent.model;

/**
 * 前端目录选择器中的一个目录节点。
 */
public class DirectoryItem {

    private String name;

    private String path;

    private boolean projectCandidate;

    public DirectoryItem(String name, String path, boolean projectCandidate) {
        this.name = name;
        this.path = path;
        this.projectCandidate = projectCandidate;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public boolean isProjectCandidate() {
        return projectCandidate;
    }
}
