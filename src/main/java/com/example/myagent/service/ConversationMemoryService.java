package com.example.myagent.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单会话记忆。
 *
 * <p>第一版使用内存保存最近几轮对话，重启后会丢失。后续可以替换成 Redis 或数据库。</p>
 */
@Service
public class ConversationMemoryService {

    private static final int MAX_MESSAGES = 6;

    private final Map<String, Deque<String>> memory = new ConcurrentHashMap<String, Deque<String>>();

    public String ensureSessionId(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }
        return UUID.randomUUID().toString();
    }

    public List<String> getRecentMessages(String sessionId) {
        Deque<String> messages = memory.get(sessionId);
        if (messages == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(messages);
    }

    public void append(String sessionId, String role, String content) {
        Deque<String> messages = memory.get(sessionId);
        if (messages == null) {
            messages = new ArrayDeque<String>();
            memory.put(sessionId, messages);
        }
        messages.addLast(role + ": " + content);
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
    }
}
