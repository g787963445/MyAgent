package com.example.myagent.service;

import com.example.myagent.model.AgentChatRequest;
import com.example.myagent.model.AgentChatResponse;
import com.example.myagent.model.ProjectContext;
import com.example.myagent.model.SourceFileContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public AgentService(ProjectContextService projectContextService,
                        ConversationMemoryService memoryService,
                        ChatClient chatClient) {
        this.projectContextService = projectContextService;
        this.memoryService = memoryService;
        this.chatClient = chatClient;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        String sessionId = memoryService.ensureSessionId(request.getSessionId());
        ProjectContext projectContext = projectContextService.buildContext(request.getProjectPath(), request.getQuestion());

        String systemPrompt = buildSystemPrompt();
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
        return response;
    }

    private String buildSystemPrompt() {
        return "你是一个 Java 项目编程助手 Agent。"
                + "你的任务是基于用户提供的项目代码上下文，帮助解释代码、定位问题、给出修改方案、生成示例代码和测试建议。"
                + "回答必须具体、可执行。"
                + "如果上下文不足，直接说明缺少哪些文件或信息，不要编造项目中不存在的代码。"
                + "涉及修改建议时，优先说明需要改哪些文件、改动原因和风险。"
                + "请使用中文回答。";
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
        builder.append("项目文件树摘要:\n").append(context.getFileTree()).append("\n");
        builder.append("相关代码文件:\n");

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
}
