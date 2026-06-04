package com.example.myagent.service;

import com.example.myagent.model.AgentChatRequest;
import com.example.myagent.model.AgentChatResponse;
import com.example.myagent.model.ChatHistoryRecord;
import com.example.myagent.model.ProjectContext;
import com.example.myagent.model.SourceFileContext;
import com.example.myagent.model.SourceFileReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 编程助手 Agent 的核心编排服务。
 *
 * <p>它负责把用户问题、本地项目上下文、最近会话记忆组织成 Prompt，
 * 然后调用 LLM，并把模型回答和引用文件返回给调用方。</p>
 */
@Service
public class AgentService {

    private final ProjectContextService projectContextService;

    private final ConversationMemoryService memoryService;

    private final ChatClient chatClient;

    private final ChatHistoryService chatHistoryService;

    public AgentService(ProjectContextService projectContextService,
                        ConversationMemoryService memoryService,
                        ChatClient chatClient,
                        ChatHistoryService chatHistoryService) {
        this.projectContextService = projectContextService;
        this.memoryService = memoryService;
        this.chatClient = chatClient;
        this.chatHistoryService = chatHistoryService;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        String sessionId = memoryService.ensureSessionId(request.getSessionId());
        ProjectContext projectContext = projectContextService.buildContext(request.getProjectPath(), request.getQuestion());

        String mode = normalizeMode(request.getMode());
        String systemPrompt = buildSystemPrompt(mode);
        String userPrompt = buildUserPrompt(request.getQuestion(), projectContext, memoryService.getRecentMessages(sessionId));
        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        memoryService.append(sessionId, "user", request.getQuestion());
        memoryService.append(sessionId, "assistant", answer);

        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(sessionId);
        response.setAnswer(answer);
        response.setProjectRoot(projectContext.getProjectRoot().toString());
        response.setUsedFiles(collectUsedFiles(projectContext));
        response.setContextFiles(collectContextFiles(projectContext));
        chatHistoryService.append(new ChatHistoryRecord(
                LocalDateTime.now(),
                sessionId,
                response.getProjectRoot(),
                request.getQuestion(),
                answer,
                response.getUsedFiles()
        ));
        return response;
    }

    private String normalizeMode(String mode) {
        return "plan".equalsIgnoreCase(mode) ? "plan" : "chat";
    }

    private String buildSystemPrompt(String mode) {
        String basePrompt = "你是一个 Java 项目编程助手 Agent。"
                + "你的任务是基于用户提供的项目代码上下文，帮助解释代码、定位问题、给出修改方案、生成示例代码和测试建议。"
                + "回答必须具体、可执行。"
                + "系统会从本地项目路径递归读取子目录和子文件。"
                + "如果某些文件只出现在文件树但没有正文，说明它们受上下文预算限制未被包含，不要说用户没有提供项目文件。"
                + "如果上下文不足，直接说明还需要查看哪些具体文件，不要编造项目中不存在的代码。"
                + "涉及修改建议时，优先说明需要改哪些文件、改动原因和风险。"
                + "请使用中文回答。";
        if (!"plan".equals(mode)) {
            return basePrompt;
        }
        return basePrompt
                + "当前是修改方案模式。请固定按以下结构回答："
                + "1. 目标理解；"
                + "2. 影响范围；"
                + "3. 需要修改的文件；"
                + "4. 每个文件的具体修改点；"
                + "5. 示例代码或伪代码；"
                + "6. 测试建议；"
                + "7. 风险与注意事项。"
                + "不要声称已经修改文件，只输出可执行方案。";
    }

    private String buildUserPrompt(String question, ProjectContext context, List<String> recentMessages) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题:\n").append(question).append("\n\n");

        if (!recentMessages.isEmpty()) {
            builder.append("最近会话:\n");
            for (String message : recentMessages) {
                builder.append(message).append('\n');
            }
            builder.append('\n');
        }

        builder.append("项目根目录:\n").append(context.getProjectRoot()).append("\n\n");
        builder.append("可读取文件总数: ").append(context.getTotalFileCount()).append("\n");
        builder.append("本次已提供正文文件数: ").append(context.getIncludedFileCount()).append("\n");
        builder.append("仅在文件树中展示、未提供正文文件数: ").append(context.getOmittedFileCount()).append("\n\n");
        builder.append("项目文件树摘要（递归扫描子目录得到）:\n").append(context.getFileTree()).append("\n");
        builder.append("已提供正文的代码/配置文件:\n");

        for (SourceFileContext file : context.getFiles()) {
            builder.append("\n--- FILE: ").append(file.getRelativePath())
                    .append(" | score=").append(file.getScore()).append(" ---\n");
            builder.append(file.getContent()).append('\n');
        }

        builder.append("\n请基于以上上下文回答用户问题。");
        return builder.toString();
    }

    private List<String> collectUsedFiles(ProjectContext context) {
        List<String> usedFiles = new ArrayList<String>();
        for (SourceFileContext file : context.getFiles()) {
            usedFiles.add(file.getRelativePath());
        }
        return usedFiles;
    }

    private List<SourceFileReference> collectContextFiles(ProjectContext context) {
        List<SourceFileReference> references = new ArrayList<SourceFileReference>();
        for (SourceFileContext file : context.getFiles()) {
            references.add(new SourceFileReference(
                    file.getRelativePath(),
                    file.getScore(),
                    file.getReason(),
                    file.getFileSize(),
                    file.getLastModified(),
                    file.isTruncated()
            ));
        }
        return references;
    }
}
