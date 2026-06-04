# MyAgent

MyAgent 是一个 Java 17 + Spring AI 编程助手 Agent。它会递归读取本地项目代码，按用户问题和上下文预算选择尽可能多的源码内容提交给 DeepSeek，然后返回解释、定位建议或修改方案。

## 技术栈

- Java 17
- Spring Boot 3.5.7
- Spring AI 1.1.7
- Spring MVC
- Spring Validation
- DeepSeek Chat Model Starter
- 本地文件扫描 + 关键词检索
- 内存会话记忆
- 项目文件索引缓存
- 问答历史 JSONL 记录
- 私钥/证书文件过滤，可配置脱敏

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
- `GET /api/agent/files`：在当前项目内搜索文件。
- `GET /api/agent/file`：读取当前项目内指定文件内容。
- `POST /api/agent/chat`：向编程助手提问。
- 自动扫描项目中的 `.java`、`.xml`、`.yaml`、`.properties`、`.md`、`.sql`、`.json` 等文件。
- 自动跳过 `.git`、`.idea`、`target`、`build`、`node_modules` 等目录。
- 允许读取 `.env`、`application-prod.*`、`application-production.*`、`application-secret.*` 等配置文件。
- 默认不脱敏配置值，方便私人助手完整分析本地项目。
- 自动跳过证书、私钥、keystore 等文件。
- 递归扫描项目子目录，文件树会展示所有可读取文件。
- 根据用户问题和上下文预算选择尽可能多的代码文件正文。
- 项目文件列表会缓存一小段时间，避免每次提问都重新全量扫描。
- 前端会展示引用文件的命中分数、命中原因和是否截断。
- 调用 DeepSeek 生成中文回答。
- 返回本次回答引用了哪些项目文件。
- 支持简单 session 记忆，同一个 `sessionId` 会保留最近几轮对话。
- 每次问答会追加保存到本地 `data/chat-history.jsonl`。
- 限制只能读取 `agent.workspace-root` 下的项目路径。
- 前端支持项目目录选择器，按 Enter 发送消息，按 Shift+Enter 换行。
- 前端支持文件搜索和文件预览。
- 前端支持 `Chat` / `Plan` 两种模式，Plan 模式会生成结构化修改方案。

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

## Agent 安全配置

```yaml
agent:
  workspace-root: D:/mycode
  context:
    max-files: 80
    max-file-chars: 12000
    max-tree-lines: 1000
    max-total-chars: 200000
    max-file-bytes: 262144
    index-cache-seconds: 30
  security:
    mask-secrets: false
    excluded-file-patterns:
      - "*.pem"
      - "*.key"
      - "*.p12"
      - "*.jks"
      - "*.keystore"
      - "id_rsa"
      - "id_ed25519"
  history:
    enabled: true
    file: data/chat-history.jsonl
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

## 前端使用

- `Choose`：选择当前项目目录。
- `Search Files`：搜索当前项目下的文件，点击结果可以预览文件内容。
- `Chat`：普通问答模式，用于解释代码、定位问题、回答项目问题。
- `Plan`：修改方案模式，用于让 Agent 输出结构化改造方案，不会自动修改文件。
- `Enter`：发送消息。
- `Shift+Enter`：换行。

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
  "mode": "chat",
  "question": "帮我找一下 DeepSeek 调用逻辑在哪里"
}
```

修改方案模式：

```json
{
  "sessionId": "dev-session-1",
  "projectPath": "D:/mycode/MyAgent",
  "mode": "plan",
  "question": "帮我设计一个历史会话列表功能"
}
```

搜索文件：

```text
GET /api/agent/files?projectPath=D:/mycode/MyAgent&query=Controller
```

读取文件：

```text
GET /api/agent/file?projectPath=D:/mycode/MyAgent&relativePath=src/main/java/com/example/myagent/controller/AgentController.java
```

## 第一版边界

- 还没有向量数据库，检索使用关键词打分。
- 还没有自动修改代码，只提供解释和修改建议。
- 会话记忆存在内存中，服务重启后丢失。
- 单次上下文大小受 `agent.context.max-files`、`agent.context.max-file-chars` 和 `agent.context.max-total-chars` 控制。

## 第二版增强

- 目录选择器只能在 `agent.workspace-root` 下浏览项目。
- 项目文件索引会短暂缓存，降低重复提问时的扫描成本。
- 检索策略加入了路径权重、内容命中、配置/接口/启动/权限/数据库等问题的关键词推断。
- 默认上下文预算扩大到最多 80 个文件、总计约 200000 字符，尽量让模型看到更多子目录源码正文。
- Prompt 会明确告诉模型：文件树来自本地递归扫描；哪些文件提供了正文，哪些只是受上下文预算限制只展示路径。
- 前端引用文件区域会展示命中原因和分数。
- 问答历史会追加保存到本地 JSONL 文件。
- 过大的文件会被跳过，避免把日志或构建产物误送给模型。
- `.env` 和生产/私密 profile 配置文件允许进入上下文。
- 证书、私钥、keystore 文件仍会被排除。
- 脱敏开关保留，可通过 `agent.security.mask-secrets` 打开。
- 前端提示当前是私人助手模式，配置文件可能会发送给 DeepSeek。

## V2.5 增强

- 增加文件搜索接口和前端搜索框。
- 增加指定文件内容读取接口和前端文件预览弹窗。
- 增加 `mode` 请求字段。
- 增加 `Chat` 普通问答模式。
- 增加 `Plan` 修改方案模式，固定输出目标理解、影响范围、修改文件、修改点、示例代码、测试建议和风险。

后续可以升级为：向量检索、AST 方法级切分、生成 patch、自动运行测试、接入 Git diff review。
