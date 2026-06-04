package com.example.myagent.model;

/**
 * 文件搜索结果。
 */
public class FileSearchItem {

    private String relativePath;

    private long fileSize;

    private long lastModified;

    public FileSearchItem(String relativePath, long fileSize, long lastModified) {
        this.relativePath = relativePath;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getLastModified() {
        return lastModified;
    }
}
