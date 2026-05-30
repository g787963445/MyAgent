package com.example.myagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置。
 *
 * <p>spring-ai-starter-model-deepseek 会根据 application.yaml 自动创建 DeepSeek ChatModel。
 * 这里再基于自动配置的 ChatClient.Builder 创建项目统一使用的 ChatClient。</p>
 */
@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
