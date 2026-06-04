package com.example.myagent.service;

import com.example.myagent.config.AgentProperties;
import com.example.myagent.model.DirectoryItem;
import com.example.myagent.model.DirectoryListResponse;
import com.example.myagent.model.FileContentResponse;
import com.example.myagent.model.FileSearchItem;
import com.example.myagent.model.FileSearchResponse;
import com.example.myagent.model.WorkspaceResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;

/**
 * 为前端提供受控的本地目录浏览能力。
 *
 * <p>浏览范围被限制在 agent.workspace-root 内，避免网页端任意读取整台机器的路径。</p>
 */
@Service
public class WorkspaceService {

    private static final Set<String> HIDDEN_DIRS = new HashSet<String>(Arrays.asList(
            ".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle", ".vscode"
    ));

    private final AgentProperties properties;

    public WorkspaceService(AgentProperties properties) {
        this.properties = properties;
    }

    public WorkspaceResponse getWorkspace() {
        return new WorkspaceResponse(toDisplayPath(workspaceRoot()));
    }

    public DirectoryListResponse listDirectories(String path) {
        Path root = workspaceRoot();
        Path current = resolveWithinWorkspace(path);
        List<DirectoryItem> directories = new ArrayList<DirectoryItem>();

        try (Stream<Path> stream = Files.list(current)) {
            stream.filter(Files::isDirectory)
                    .filter(this::isVisibleDirectory)
                    .sorted(Comparator.comparing(item -> item.getFileName().toString().toLowerCase()))
                    .forEach(item -> directories.add(new DirectoryItem(
                            item.getFileName().toString(),
                            toDisplayPath(item),
                            isProjectCandidate(item)
                    )));
        } catch (IOException e) {
            throw new IllegalStateException("读取目录失败: " + e.getMessage(), e);
        }

        Path parent = current.getParent();
        String parentPath = parent != null && parent.startsWith(root) && !current.equals(root)
                ? toDisplayPath(parent)
                : null;
        return new DirectoryListResponse(toDisplayPath(root), toDisplayPath(current), parentPath, directories);
    }

    public FileSearchResponse searchFiles(String projectPath, String query) {
        Path projectRoot = resolveWithinWorkspace(projectPath);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
        List<FileSearchItem> files = new ArrayList<FileSearchItem>();

        try (Stream<Path> stream = Files.walk(projectRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isInHiddenDirectory(projectRoot, path))
                    .filter(path -> matchesQuery(projectRoot, path, normalizedQuery))
                    .limit(200)
                    .forEach(path -> files.add(toFileSearchItem(projectRoot, path)));
        } catch (IOException e) {
            throw new IllegalStateException("搜索文件失败: " + e.getMessage(), e);
        }

        files.sort(Comparator.comparing(FileSearchItem::getRelativePath));
        return new FileSearchResponse(toDisplayPath(projectRoot), query, files);
    }

    public FileContentResponse readFile(String projectPath, String relativePath) {
        Path projectRoot = resolveWithinWorkspace(projectPath);
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("relativePath 不能为空");
        }

        Path file = projectRoot.resolve(relativePath).normalize();
        if (!file.startsWith(projectRoot)) {
            throw new IllegalArgumentException("文件路径超出项目目录: " + relativePath);
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在或不是普通文件: " + relativePath);
        }
        if (isInHiddenDirectory(projectRoot, file)) {
            throw new IllegalArgumentException("该文件位于隐藏目录，不能读取: " + relativePath);
        }

        try {
            long fileSize = Files.size(file);
            int maxChars = properties.getContext().getMaxFileChars();
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            boolean truncated = content.length() > maxChars;
            if (truncated) {
                content = content.substring(0, maxChars) + "\n\n// ... file truncated by MyAgent ...";
            }
            return new FileContentResponse(toDisplayPath(projectRoot), normalizeRelative(projectRoot, file), content, truncated, fileSize);
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + e.getMessage(), e);
        }
    }

    private Path workspaceRoot() {
        Path root = Paths.get(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("workspace-root 不存在或不是目录: " + root);
        }
        return root;
    }

    private Path resolveWithinWorkspace(String path) {
        Path root = workspaceRoot();
        Path target = StringUtils.hasText(path) ? Paths.get(path).toAbsolutePath().normalize() : root;

        if (!Files.exists(target) || !Files.isDirectory(target)) {
            throw new IllegalArgumentException("目录不存在或不是目录: " + target);
        }
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("目录超出允许的 workspace-root: " + root);
        }
        return target;
    }

    private boolean isVisibleDirectory(Path path) {
        return !HIDDEN_DIRS.contains(path.getFileName().toString());
    }

    private boolean isInHiddenDirectory(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (HIDDEN_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesQuery(Path projectRoot, Path path, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        return normalizeRelative(projectRoot, path).toLowerCase(Locale.ENGLISH).contains(query);
    }

    private FileSearchItem toFileSearchItem(Path projectRoot, Path path) {
        try {
            return new FileSearchItem(normalizeRelative(projectRoot, path), Files.size(path), Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            return new FileSearchItem(normalizeRelative(projectRoot, path), 0L, 0L);
        }
    }

    private boolean isProjectCandidate(Path path) {
        return Files.exists(path.resolve("pom.xml"))
                || Files.exists(path.resolve("build.gradle"))
                || Files.exists(path.resolve("settings.gradle"))
                || Files.isDirectory(path.resolve("src"));
    }

    private String toDisplayPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private String normalizeRelative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
