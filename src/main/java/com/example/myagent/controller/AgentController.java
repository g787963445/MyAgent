package com.example.myagent.controller;

import com.example.myagent.model.AgentChatRequest;
import com.example.myagent.model.AgentChatResponse;
import com.example.myagent.model.DirectoryListResponse;
import com.example.myagent.model.WorkspaceResponse;
import com.example.myagent.service.AgentService;
import com.example.myagent.service.WorkspaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 编程助手 Agent 的 HTTP 入口。
 *
 * <p>原来的 Java 项目、Postman、前端页面或命令行工具都可以通过这个 Controller
 * 调用 Agent 能力。</p>
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    private final WorkspaceService workspaceService;

    public AgentController(AgentService agentService, WorkspaceService workspaceService) {
        this.agentService = agentService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/health")
    public String health() {
        return "MyAgent is running";
    }

    @GetMapping("/workspace")
    public WorkspaceResponse workspace() {
        return workspaceService.getWorkspace();
    }

    @GetMapping("/directories")
    public DirectoryListResponse directories(@RequestParam(value = "path", required = false) String path) {
        return workspaceService.listDirectories(path);
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return agentService.chat(request);
    }
}
