package com.example.myagent.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次 Agent 调用所需的项目上下文。
 */
public class ProjectContext {

    private Path projectRoot;

    private String fileTree;

    private List<SourceFileContext> files = new ArrayList<SourceFileContext>();

    public ProjectContext(Path projectRoot, String fileTree, List<SourceFileContext> files) {
        this.projectRoot = projectRoot;
        this.fileTree = fileTree;
        this.files = files;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public String getFileTree() {
        return fileTree;
    }

    public List<SourceFileContext> getFiles() {
        return files;
    }
}
