package com.example.myagent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件搜索响应。
 */
public class FileSearchResponse {

    private String projectRoot;

    private String query;

    private List<FileSearchItem> files = new ArrayList<FileSearchItem>();

    public FileSearchResponse(String projectRoot, String query, List<FileSearchItem> files) {
        this.projectRoot = projectRoot;
        this.query = query;
        this.files = files;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public String getQuery() {
        return query;
    }

    public List<FileSearchItem> getFiles() {
        return files;
    }
}
