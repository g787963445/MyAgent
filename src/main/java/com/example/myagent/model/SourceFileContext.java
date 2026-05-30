package com.example.myagent.model;

/**
 * 被检索出来的源码片段。
 *
 * <p>第一版按文件维度截取内容；后续可以升级为按类、方法或 AST 节点切分。</p>
 */
public class SourceFileContext {

    private String relativePath;

    private String content;

    private int score;

    public SourceFileContext(String relativePath, String content, int score) {
        this.relativePath = relativePath;
        this.content = content;
        this.score = score;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getContent() {
        return content;
    }

    public int getScore() {
        return score;
    }
}
