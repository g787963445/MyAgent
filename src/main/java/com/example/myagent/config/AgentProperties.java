package com.example.myagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private Security security = new Security();

    private History history = new History();

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

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public History getHistory() {
        return history;
    }

    public void setHistory(History history) {
        this.history = history;
    }

    public static class Context {
        /**
         * 每次请求最多送给大模型的相关文件数量。
         */
        private int maxFiles = 80;

        /**
         * 单个文件最多截取的字符数，防止超出模型上下文。
         */
        private int maxFileChars = 12000;

        /**
         * 项目目录树最多展示多少行。
         */
        private int maxTreeLines = 1000;

        /**
         * 单次请求最多送给大模型的源码总字符数。
         */
        private int maxTotalChars = 200000;

        /**
         * 单个文件最大读取字节数，过大的文件会被跳过。
         */
        private long maxFileBytes = 262144;

        /**
         * 项目文件索引缓存时间，单位秒。
         */
        private long indexCacheSeconds = 30;

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

        public int getMaxTotalChars() {
            return maxTotalChars;
        }

        public void setMaxTotalChars(int maxTotalChars) {
            this.maxTotalChars = maxTotalChars;
        }

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public long getIndexCacheSeconds() {
            return indexCacheSeconds;
        }

        public void setIndexCacheSeconds(long indexCacheSeconds) {
            this.indexCacheSeconds = indexCacheSeconds;
        }
    }

    public static class Security {
        /**
         * 是否在发送给大模型前脱敏常见密钥、密码、token。
         */
        private boolean maskSecrets = false;

        /**
         * 不允许发送给模型的敏感文件名或 glob 模式。
         */
        private List<String> excludedFilePatterns = new ArrayList<String>(Arrays.asList(
                "*.pem",
                "*.key",
                "*.p12",
                "*.jks",
                "*.keystore",
                "id_rsa",
                "id_ed25519"
        ));

        public boolean isMaskSecrets() {
            return maskSecrets;
        }

        public void setMaskSecrets(boolean maskSecrets) {
            this.maskSecrets = maskSecrets;
        }

        public List<String> getExcludedFilePatterns() {
            return excludedFilePatterns;
        }

        public void setExcludedFilePatterns(List<String> excludedFilePatterns) {
            this.excludedFilePatterns = excludedFilePatterns;
        }
    }

    public static class History {
        /**
         * 是否把问答历史追加保存到本地 JSONL 文件。
         */
        private boolean enabled = true;

        /**
         * 历史记录文件路径。相对路径会基于应用启动目录解析。
         */
        private String file = "data/chat-history.jsonl";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }

}
