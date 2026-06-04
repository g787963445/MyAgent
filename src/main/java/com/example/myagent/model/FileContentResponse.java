package com.example.myagent.model;

/**
 * 指定文件内容响应。
 */
public class FileContentResponse {

    private String projectRoot;

    private String relativePath;

    private String content;

    private boolean truncated;

    private long fileSize;

    public FileContentResponse(String projectRoot, String relativePath, String content, boolean truncated, long fileSize) {
        this.projectRoot = projectRoot;
        this.relativePath = relativePath;
        this.content = content;
        this.truncated = truncated;
        this.fileSize = fileSize;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getContent() {
        return content;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public long getFileSize() {
        return fileSize;
    }
}
