package com.example.myagent.service;

import com.example.myagent.config.AgentProperties;
import com.example.myagent.model.ProjectContext;
import com.example.myagent.model.SourceFileContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    public ProjectContextService(AgentProperties properties) {
        this.properties = properties;
    }

    public ProjectContext buildContext(String projectPath, String question) {
        try {
            Path root = resolveProjectRoot(projectPath);
            List<Path> files = collectSupportedFiles(root);
            String tree = buildFileTree(root, files);
            List<String> keywords = extractKeywords(question);
            List<SourceFileContext> selectedFiles = selectRelevantFiles(root, files, keywords);
            return new ProjectContext(root, tree, selectedFiles);
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

    private List<Path> collectSupportedFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .filter(path -> !isInSkippedDir(root, path))
                    .forEach(result::add);
        }
        Collections.sort(result);
        return result;
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
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

    private String buildFileTree(Path root, List<Path> files) {
        StringBuilder builder = new StringBuilder();
        int maxLines = properties.getContext().getMaxTreeLines();
        int count = 0;
        for (Path file : files) {
            if (count >= maxLines) {
                builder.append("... more files omitted\n");
                break;
            }
            builder.append(root.relativize(file).toString().replace('\\', '/')).append('\n');
            count++;
        }
        return builder.toString();
    }

    private List<String> extractKeywords(String question) {
        List<String> keywords = new ArrayList<String>();
        Matcher matcher = WORD_PATTERN.matcher(question == null ? "" : question);
        while (matcher.find()) {
            String word = matcher.group().trim().toLowerCase(Locale.ENGLISH);
            if (word.length() >= 2 && !keywords.contains(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    private List<SourceFileContext> selectRelevantFiles(Path root, List<Path> files, List<String> keywords) throws IOException {
        List<SourceFileContext> scoredFiles = new ArrayList<SourceFileContext>();
        int maxFileChars = properties.getContext().getMaxFileChars();

        for (Path file : files) {
            String relativePath = root.relativize(file).toString().replace('\\', '/');
            String content = readFile(file, maxFileChars);
            int score = score(relativePath, content, keywords);
            if (score > 0 || isImportantProjectFile(relativePath)) {
                scoredFiles.add(new SourceFileContext(relativePath, content, score));
            }
        }

        Collections.sort(scoredFiles, new Comparator<SourceFileContext>() {
            @Override
            public int compare(SourceFileContext left, SourceFileContext right) {
                return Integer.compare(right.getScore(), left.getScore());
            }
        });

        int maxFiles = Math.min(properties.getContext().getMaxFiles(), scoredFiles.size());
        return new ArrayList<SourceFileContext>(scoredFiles.subList(0, maxFiles));
    }

    private String readFile(Path file, int maxChars) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "\n\n// ... file truncated by MyAgent ...";
    }

    private int score(String relativePath, String content, List<String> keywords) {
        String lowerPath = relativePath.toLowerCase(Locale.ENGLISH);
        String lowerContent = content.toLowerCase(Locale.ENGLISH);
        int score = 0;
        for (String keyword : keywords) {
            if (lowerPath.contains(keyword)) {
                score += 10;
            }
            score += countOccurrences(lowerContent, keyword);
        }
        if (relativePath.endsWith("pom.xml")) {
            score += 3;
        }
        return score;
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
}
