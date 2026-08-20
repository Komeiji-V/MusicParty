package org.thornex.musicparty.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.thornex.musicparty.config.AppProperties;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 认证中心客户端：验证 token、刷新 token。
 * 对接自研统一登录平台 (C:\Project\Auth)，契约见 docs/API.md。
 */
@Service
@Slf4j
public class AuthCenterClient {

    private final WebClient webClient;
    private final String baseUrl;

    public AuthCenterClient(AppProperties appProperties) {
        this.baseUrl = appProperties.getAuthCenter().getUrl();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 验证 access token 并获取用户信息。
     * 成功: 返回 { id, username, email, role, status }
     * 失败: 返回 null (401 过期/版本不符 / 403 pending/banned)
     */
    public Map<String, Object> verifyToken(String accessToken) {
        try {
            return webClient.get()
                    .uri("/api/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(),
                            response -> response.bodyToMono(String.class).map(RuntimeException::new))
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.debug("Auth center token verification failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 刷新 token（轮换制）。
     * 成功: 返回 { token, refresh_token, expires_in }
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            return webClient.post()
                    .uri("/api/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("refresh_token", refreshToken))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.debug("Auth center refresh failed: {}", e.getMessage());
            return null;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
