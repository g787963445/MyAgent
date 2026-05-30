# MyAgent

MyAgent 是一个 Java 17 + Spring AI 编程助手 Agent。它会读取本地项目代码，按用户问题检索相关文件，把代码上下文提交给 DeepSeek，然后返回解释、定位建议或修改方案。

## 技术栈

- Java 17
- Spring Boot 3.5.7
- Spring AI 1.1.7
- Spring MVC
- Spring Validation
- DeepSeek Chat Model Starter
- 本地文件扫描 + 关键词检索
- 内存会话记忆

## 目录结构

```text
src/main/java/com/example/myagent
├── MyAgentApplication.java
├── config
│   ├── AgentProperties.java
│   └── SpringAiConfig.java
├── controller
│   ├── AgentController.java
│   ├── ApiError.java
│   └── GlobalExceptionHandler.java
├── model
│   ├── AgentChatRequest.java
│   ├── AgentChatResponse.java
│   ├── ProjectContext.java
│   └── SourceFileContext.java
└── service
    ├── AgentService.java
    ├── ConversationMemoryService.java
    └── ProjectContextService.java
```

## 已实现功能

- `GET /`：内置前端聊天页面。
- `GET /api/agent/health`：健康检查。
- `GET /api/agent/workspace`：查看允许浏览和读取的工作区根目录。
- `GET /api/agent/directories`：列出工作区内的子目录，供前端选择项目路径。
- `POST /api/agent/chat`：向编程助手提问。
- 自动扫描项目中的 `.java`、`.xml`、`.yaml`、`.properties`、`.md`、`.sql`、`.json` 等文件。
- 自动跳过 `.git`、`.idea`、`target`、`build`、`node_modules` 等目录。
- 根据用户问题选择最相关的代码文件作为上下文。
- 调用 DeepSeek 生成中文回答。
- 返回本次回答引用了哪些项目文件。
- 支持简单 session 记忆，同一个 `sessionId` 会保留最近几轮对话。
- 限制只能读取 `agent.workspace-root` 下的项目路径。
- 前端支持项目目录选择器，按 Enter 发送消息，按 Shift+Enter 换行。

## DeepSeek 配置

配置位于 `src/main/resources/application.yaml`：

```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.2
          max-tokens: 2048
```

推荐使用环境变量配置 API Key：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
```

## 启动

```powershell
.\mvnw.cmd spring-boot:run
```

启动后访问：

```text
http://localhost:8080/
```

左侧点击 `Choose` 可以在 `agent.workspace-root` 范围内选择项目目录。

## 调用示例

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/agent/chat" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{
    "sessionId": "dev-session-1",
    "question": "帮我解释这个项目的主要结构，以及 AgentController 是怎么工作的"
  }'
```

也可以指定项目路径，但必须在 `agent.workspace-root` 下：

```json
{
  "sessionId": "dev-session-1",
  "projectPath": "D:/mycode/MyAgent",
  "question": "帮我找一下 DeepSeek 调用逻辑在哪里"
}
```

## 第一版边界

- 还没有向量数据库，检索使用关键词打分。
- 还没有自动修改代码，只提供解释和修改建议。
- 会话记忆存在内存中，服务重启后丢失。
- 单次上下文大小受 `agent.context.max-files` 和 `agent.context.max-file-chars` 控制。

后续可以升级为：向量检索、AST 方法级切分、生成 patch、自动运行测试、接入 Git diff review。
