package com.example.myagent.service;

import com.example.myagent.config.AgentProperties;
import com.example.myagent.model.ChatHistoryRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 轻量本地历史记录服务。
 *
 * <p>每次问答追加为一行 JSON，便于后续扩展历史会话列表或导出。</p>
 */
@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);

    private final AgentProperties properties;

    private final ObjectMapper objectMapper;

    public ChatHistoryService(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void append(ChatHistoryRecord record) {
        if (!properties.getHistory().isEnabled()) {
            return;
        }

        try {
            Path file = Paths.get(properties.getHistory().getFile()).toAbsolutePath().normalize();
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("保存会话历史失败: {}", e.getMessage());
        }
    }
}
