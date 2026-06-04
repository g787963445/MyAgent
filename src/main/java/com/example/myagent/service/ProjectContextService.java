package com.example.myagent.service;

import com.example.myagent.config.AgentProperties;
import com.example.myagent.model.ProjectContext;
import com.example.myagent.model.SourceFileContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 本地项目上下文检索服务。
 *
 * <p>第一版使用轻量方案：扫描文件树、抽取问题关键词、按路径和内容命中次数打分，
 * 选出最相关的若干文件给 LLM。这样不用引入向量库，也能先把 Agent 跑起来。</p>
 */
@Service
public class ProjectContextService {

    private static final Set<String> SKIPPED_DIRS = new HashSet<String>(Arrays.asList(
            ".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle", ".vscode"
    ));

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>(Arrays.asList(
            ".java", ".xml", ".yaml", ".yml", ".properties", ".md", ".txt", ".sql", ".json"
    ));

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z0-9_\\-.\\u4e00-\\u9fa5]+");

    private final AgentProperties properties;

    private final SensitiveDataMasker sensitiveDataMasker;

    private final Map<String, ProjectFileIndex> indexCache = new ConcurrentHashMap<String, ProjectFileIndex>();

    public ProjectContextService(AgentProperties properties, SensitiveDataMasker sensitiveDataMasker) {
        this.properties = properties;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    public ProjectContext buildContext(String projectPath, String question) {
        try {
            Path root = resolveProjectRoot(projectPath);
            List<FileEntry> files = getIndexedFiles(root);
            String tree = buildFileTree(root, files);
            List<String> keywords = extractKeywords(question);
            List<SourceFileContext> selectedFiles = selectRelevantFiles(root, files, keywords);
            return new ProjectContext(root, tree, selectedFiles, files.size());
        } catch (IOException e) {
            throw new IllegalStateException("读取项目代码失败: " + e.getMessage(), e);
        }
    }

    private Path resolveProjectRoot(String projectPath) throws IOException {
        Path workspaceRoot = Paths.get(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
        String target = StringUtils.hasText(projectPath) ? projectPath : properties.getWorkspaceRoot();
        Path projectRoot = Paths.get(target).toAbsolutePath().normalize();

        if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("项目路径不存在或不是目录: " + projectRoot);
        }
        if (!projectRoot.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("项目路径超出允许的 workspace-root: " + workspaceRoot);
        }
        return projectRoot;
    }

    private List<FileEntry> getIndexedFiles(Path root) throws IOException {
        String cacheKey = root.toString();
        ProjectFileIndex cached = indexCache.get(cacheKey);
        if (cached != null && !cached.isExpired(properties.getContext().getIndexCacheSeconds())) {
            return cached.getFiles();
        }

        List<FileEntry> files = collectSupportedFiles(root);
        indexCache.put(cacheKey, new ProjectFileIndex(files));
        return files;
    }

    private List<FileEntry> collectSupportedFiles(Path root) throws IOException {
        List<FileEntry> result = new ArrayList<FileEntry>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .filter(path -> !isInSkippedDir(root, path))
                    .filter(path -> !isExcludedFile(root, path))
                    .filter(this::isWithinSizeLimit)
                    .forEach(path -> result.add(toFileEntry(root, path)));
        }
        Collections.sort(result, Comparator.comparing(FileEntry::getRelativePath));
        return result;
    }

    private FileEntry toFileEntry(Path root, Path path) {
        try {
            return new FileEntry(
                    path,
                    root.relativize(path).toString().replace('\\', '/'),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toMillis()
            );
        } catch (IOException e) {
            return new FileEntry(path, root.relativize(path).toString().replace('\\', '/'), 0L, 0L);
        }
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
        if (".env".equals(name) || name.startsWith(".env.")) {
            return true;
        }
        for (String extension : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInSkippedDir(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcludedFile(Path root, Path path) {
        Path relativePath = root.relativize(path);
        String normalizedRelativePath = relativePath.toString().replace('\\', '/');
        String fileName = path.getFileName().toString();

        for (String pattern : properties.getSecurity().getExcludedFilePatterns()) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            if (matcher.matches(path.getFileName()) || matcher.matches(relativePath)) {
                return true;
            }
            if (matchesNormalizedGlob(pattern, normalizedRelativePath) || matchesNormalizedGlob(pattern, fileName)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesNormalizedGlob(String pattern, String value) {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < pattern.length(); index++) {
            char current = pattern.charAt(index);
            if (current == '*') {
                boolean doubleStar = index + 1 < pattern.length() && pattern.charAt(index + 1) == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) {
                    index++;
                }
            } else if (".[]{}()+-^$|\\".indexOf(current) >= 0) {
                regex.append('\\').append(current);
            } else {
                regex.append(current);
            }
        }
        return value.matches(regex.toString());
    }

    private boolean isWithinSizeLimit(Path path) {
        try {
            return Files.size(path) <= properties.getContext().getMaxFileBytes();
        } catch (IOException e) {
            return false;
        }
    }

    private String buildFileTree(Path root, List<FileEntry> files) {
        StringBuilder builder = new StringBuilder();
        int maxLines = properties.getContext().getMaxTreeLines();
        int count = 0;
        for (FileEntry file : files) {
            if (count >= maxLines) {
                builder.append("... more files omitted\n");
                break;
            }
            builder.append(file.getRelativePath()).append('\n');
            count++;
        }
        return builder.toString();
    }

    private List<String> extractKeywords(String question) {
        Set<String> keywords = new LinkedHashSet<String>();
        Matcher matcher = WORD_PATTERN.matcher(question == null ? "" : question);
        while (matcher.find()) {
            String word = matcher.group().trim().toLowerCase(Locale.ENGLISH);
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }
        addInferredKeywords(question, keywords);
        return new ArrayList<String>(keywords);
    }

    private void addInferredKeywords(String question, Set<String> keywords) {
        String text = question == null ? "" : question.toLowerCase(Locale.ENGLISH);
        if (text.contains("接口") || text.contains("api") || text.contains("controller")) {
            keywords.add("controller");
            keywords.add("requestmapping");
            keywords.add("getmapping");
            keywords.add("postmapping");
        }
        if (text.contains("配置") || text.contains("环境") || text.contains("profile") || text.contains("yaml")) {
            keywords.add("application");
            keywords.add("properties");
            keywords.add("yaml");
            keywords.add("config");
        }
        if (text.contains("启动") || text.contains("main") || text.contains("springapplication")) {
            keywords.add("application");
            keywords.add("springapplication");
            keywords.add("pom");
        }
        if (text.contains("登录") || text.contains("权限") || text.contains("鉴权") || text.contains("security")) {
            keywords.add("login");
            keywords.add("auth");
            keywords.add("security");
            keywords.add("token");
        }
        if (text.contains("数据库") || text.contains("sql") || text.contains("mapper")) {
            keywords.add("mapper");
            keywords.add("repository");
            keywords.add("datasource");
            keywords.add("sql");
        }
    }

    private List<SourceFileContext> selectRelevantFiles(Path root, List<FileEntry> files, List<String> keywords) throws IOException {
        List<SourceFileContext> scoredFiles = new ArrayList<SourceFileContext>();
        int maxFileChars = properties.getContext().getMaxFileChars();

        for (FileEntry file : files) {
            ReadFileResult readResult = readFile(file.getPath(), maxFileChars);
            ScoreResult scoreResult = score(file.getRelativePath(), readResult.getContent(), keywords);
            int score = scoreResult.getScore();
            String reason = score > 0 ? scoreResult.getReason() : "included for broad project context";
            if (isImportantProjectFile(file.getRelativePath()) && score == 0) {
                score = 3;
                reason = "important project file";
            }
            scoredFiles.add(new SourceFileContext(
                    file.getRelativePath(),
                    readResult.getContent(),
                    score,
                    reason,
                    file.getFileSize(),
                    file.getLastModified(),
                    readResult.isTruncated()
            ));
        }

        Collections.sort(scoredFiles, new Comparator<SourceFileContext>() {
            @Override
            public int compare(SourceFileContext left, SourceFileContext right) {
                return Integer.compare(right.getScore(), left.getScore());
            }
        });

        return selectWithinBudget(scoredFiles);
    }

    private List<SourceFileContext> selectWithinBudget(List<SourceFileContext> scoredFiles) {
        List<SourceFileContext> selected = new ArrayList<SourceFileContext>();
        int maxFiles = properties.getContext().getMaxFiles();
        int maxTotalChars = properties.getContext().getMaxTotalChars();
        int totalChars = 0;

        for (SourceFileContext file : scoredFiles) {
            if (selected.size() >= maxFiles) {
                break;
            }
            int contentLength = file.getContent() == null ? 0 : file.getContent().length();
            if (!selected.isEmpty() && totalChars + contentLength > maxTotalChars) {
                continue;
            }
            selected.add(file);
            totalChars += contentLength;
        }
        return selected;
    }

    private ReadFileResult readFile(Path file, int maxChars) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.length() <= maxChars) {
            return new ReadFileResult(sensitiveDataMasker.mask(content), false);
        }
        String truncated = sensitiveDataMasker.mask(content.substring(0, maxChars)) + "\n\n// ... file truncated by MyAgent ...";
        return new ReadFileResult(truncated, true);
    }

    private ScoreResult score(String relativePath, String content, List<String> keywords) {
        String lowerPath = relativePath.toLowerCase(Locale.ENGLISH);
        String lowerContent = content.toLowerCase(Locale.ENGLISH);
        int score = 0;
        List<String> reasons = new ArrayList<String>();
        for (String keyword : keywords) {
            if (lowerPath.contains(keyword)) {
                score += 10;
                reasons.add("path:" + keyword);
            }
            int hits = countOccurrences(lowerContent, keyword);
            if (hits > 0) {
                score += hits;
                reasons.add("content:" + keyword + "(" + hits + ")");
            }
        }
        if (relativePath.endsWith("pom.xml")) {
            score += 3;
            reasons.add("build file");
        }
        if (lowerPath.contains("/controller/") || lowerPath.endsWith("controller.java")) {
            score += containsAny(keywords, "接口", "api", "controller") ? 8 : 0;
        }
        if (lowerPath.contains("/service/") || lowerPath.endsWith("service.java")) {
            score += containsAny(keywords, "service", "业务", "逻辑") ? 8 : 0;
        }
        if (lowerPath.contains("application") && (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml") || lowerPath.endsWith(".properties"))) {
            score += containsAny(keywords, "配置", "config", "application", "环境", "profile") ? 8 : 0;
        }
        return new ScoreResult(score, reasons.isEmpty() ? "important project file" : String.join(", ", reasons));
    }

    private boolean containsAny(List<String> keywords, String... values) {
        for (String value : values) {
            if (keywords.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) >= 0) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    private boolean isImportantProjectFile(String relativePath) {
        return "pom.xml".equals(relativePath)
                || "build.gradle".equals(relativePath)
                || "settings.gradle".equals(relativePath)
                || relativePath.endsWith("application.yaml")
                || relativePath.endsWith("application.yml")
                || relativePath.endsWith("application.properties");
    }

    private static class ProjectFileIndex {
        private final List<FileEntry> files;
        private final long createdAt;

        private ProjectFileIndex(List<FileEntry> files) {
            this.files = files;
            this.createdAt = System.currentTimeMillis();
        }

        private List<FileEntry> getFiles() {
            return files;
        }

        private boolean isExpired(long cacheSeconds) {
            return cacheSeconds <= 0 || System.currentTimeMillis() - createdAt > cacheSeconds * 1000;
        }
    }

    private static class FileEntry {
        private final Path path;
        private final String relativePath;
        private final long fileSize;
        private final long lastModified;

        private FileEntry(Path path, String relativePath, long fileSize, long lastModified) {
            this.path = path;
            this.relativePath = relativePath;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
        }

        private Path getPath() {
            return path;
        }

        private String getRelativePath() {
            return relativePath;
        }

        private long getFileSize() {
            return fileSize;
        }

        private long getLastModified() {
            return lastModified;
        }
    }

    private static class ReadFileResult {
        private final String content;
        private final boolean truncated;

        private ReadFileResult(String content, boolean truncated) {
            this.content = content;
            this.truncated = truncated;
        }

        private String getContent() {
            return content;
        }

        private boolean isTruncated() {
            return truncated;
        }
    }

    private static class ScoreResult {
        private final int score;
        private final String reason;

        private ScoreResult(int score, String reason) {
            this.score = score;
            this.reason = reason;
        }

        private int getScore() {
            return score;
        }

        private String getReason() {
            return reason;
        }
    }
}
