package com.example.myagent.service;

import com.example.myagent.config.AgentProperties;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 发送给大模型前的敏感信息脱敏器。
 *
 * <p>这里处理常见的 key、token、password、secret 等配置项。
 * 它不是审计级 DLP，但能显著降低项目配置被直接发送出去的风险。</p>
 */
@Service
public class SensitiveDataMasker {

    private final AgentProperties properties;

    private final List<Pattern> secretPatterns = Arrays.asList(
            Pattern.compile("(?im)^\\s*([A-Za-z0-9_.-]*(?:api[-_]?key|secret|token|password|passwd|pwd|credential)[A-Za-z0-9_.-]*\\s*[:=]\\s*)([^\\s#]+)"),
            Pattern.compile("(?im)([\"']?(?:api[-_]?key|secret|token|password|passwd|pwd|credential)[\"']?\\s*[:=]\\s*[\"'])([^\"']+)([\"'])"),
            Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9._~+\\-/=]{16,})"),
            Pattern.compile("(?i)(sk-[A-Za-z0-9]{12,})")
    );

    public SensitiveDataMasker(AgentProperties properties) {
        this.properties = properties;
    }

    public String mask(String content) {
        if (!properties.getSecurity().isMaskSecrets() || content == null || content.isEmpty()) {
            return content;
        }

        String masked = content;
        for (Pattern pattern : secretPatterns) {
            masked = pattern.matcher(masked).replaceAll(match -> {
                if (match.groupCount() >= 3) {
                    return match.group(1) + "***MASKED***" + match.group(3);
                }
                if (match.groupCount() >= 2) {
                    return match.group(1) + "***MASKED***";
                }
                return "***MASKED***";
            });
        }
        return masked;
    }
}
