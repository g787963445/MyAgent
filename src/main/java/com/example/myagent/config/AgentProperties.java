package com.example.myagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 的统一配置入口。
 *
 * <p>所有可调整参数都放在 application.yaml 里，业务代码只依赖这个配置类，
 * 后续替换模型、调整上下文长度或限制工作目录时不需要改核心逻辑。</p>
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /**
     * Agent 允许读取的项目根目录。用户传入的 projectPath 必须位于这个目录内。
     */
    private String workspaceRoot = ".";

    private Context context = new Context();

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public static class Context {
        /**
         * 每次请求最多送给大模型的相关文件数量。
         */
        private int maxFiles = 8;

        /**
         * 单个文件最多截取的字符数，防止超出模型上下文。
         */
        private int maxFileChars = 6000;

        /**
         * 项目目录树最多展示多少行。
         */
        private int maxTreeLines = 120;

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public int getMaxFileChars() {
            return maxFileChars;
        }

        public void setMaxFileChars(int maxFileChars) {
            this.maxFileChars = maxFileChars;
        }

        public int getMaxTreeLines() {
            return maxTreeLines;
        }

        public void setMaxTreeLines(int maxTreeLines) {
            this.maxTreeLines = maxTreeLines;
        }
    }

}
