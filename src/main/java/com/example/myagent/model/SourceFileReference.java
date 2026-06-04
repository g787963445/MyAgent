package com.example.myagent.model;

/**
 * 返回给前端的引用文件摘要。
 *
 * <p>不包含文件正文，只展示本次回答为什么参考了这个文件。</p>
 */
public class SourceFileReference {

    private String relativePath;

    private int score;

    private String reason;

    private long fileSize;

    private long lastModified;

    private boolean truncated;

    public SourceFileReference(String relativePath, int score, String reason, long fileSize, long lastModified, boolean truncated) {
        this.relativePath = relativePath;
        this.score = score;
        this.reason = reason;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.truncated = truncated;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public int getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
