package org.thornex.musicparty.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thornex.musicparty.config.SecurityConfig;
import org.thornex.musicparty.entity.Channel;
import org.thornex.musicparty.service.ChannelService;
import org.thornex.musicparty.service.SystemConfigService;
import org.thornex.musicparty.service.api.MusicProvider;
import org.thornex.musicparty.service.api.MusicProviderFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final SystemConfigService systemConfigService;
    private final ChannelService channelService;
    private final MusicProviderFactory musicProviderFactory;

    @PutMapping("/config/site")
    public ResponseEntity<?> updateSiteConfig(@RequestBody Map<String, String> body) {
        if (body.containsKey("siteTitle")) {
            systemConfigService.setSiteTitle(body.get("siteTitle"));
        }
        if (body.containsKey("authorName")) {
            systemConfigService.setAuthorName(body.get("authorName"));
        }
        if (body.containsKey("backWords")) {
            systemConfigService.setBackWords(body.get("backWords"));
        }
        if (body.containsKey("infoPageContent")) {
            systemConfigService.setInfoPageContent(body.get("infoPageContent"));
        }
        if (body.containsKey("aboutText")) {
            systemConfigService.setAboutText(body.get("aboutText"));
        }
        return ResponseEntity.ok(Map.of("message", "站点配置已更新"));
    }

    @PostMapping("/config/cookie")
    public ResponseEntity<?> updateCookie(@RequestBody Map<String, String> body) {
        String platform = body.get("platform");
        String cookieValue = body.get("cookieValue");
        if (cookieValue == null) {
            cookieValue = body.get("value");
        }
        if (platform == null || platform.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "平台不能为空"));
        }

        MusicProvider provider = musicProviderFactory.getProvider(platform);
        provider.setCookie(cookieValue != null ? cookieValue : "");
        return ResponseEntity.ok(Map.of("message", "平台 Cookie 已更新"));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(systemConfigService.getStats());
    }

    @DeleteMapping("/cleanup/chat")
    public ResponseEntity<?> cleanupChat(@RequestBody Map<String, Object> body) {
        Integer olderThanDays = body.containsKey("olderThanDays") ? ((Number) body.get("olderThanDays")).intValue() : null;
        Long channelId = body.containsKey("channelId") ? ((Number) body.get("channelId")).longValue() : null;
        int count = systemConfigService.cleanupChatMessages(olderThanDays, channelId);
        return ResponseEntity.ok(Map.of("message", "已清理 " + count + " 条聊天记录", "count", count));
    }

    @DeleteMapping("/cleanup/history")
    public ResponseEntity<?> cleanupHistory(@RequestBody Map<String, Object> body) {
        Integer olderThanDays = body.containsKey("olderThanDays") ? ((Number) body.get("olderThanDays")).intValue() : null;
        Long channelId = body.containsKey("channelId") ? ((Number) body.get("channelId")).longValue() : null;
        int count = systemConfigService.cleanupPlayHistory(olderThanDays, channelId);
        return ResponseEntity.ok(Map.of("message", "已清理 " + count + " 条播放历史", "count", count));
    }

    @DeleteMapping("/cleanup/queue")
    public ResponseEntity<?> cleanupQueue(@RequestBody Map<String, Object> body) {
        Long channelId = body.containsKey("channelId") ? ((Number) body.get("channelId")).longValue() : null;
        int count = systemConfigService.cleanupQueueItems(channelId);
        return ResponseEntity.ok(Map.of("message", "已清理 " + count + " 条队列项", "count", count));
    }

    @DeleteMapping("/cleanup/cache")
    public ResponseEntity<?> cleanupCache(@RequestBody Map<String, Object> body) {
        Integer olderThanDays = body.containsKey("olderThanDays") ? ((Number) body.get("olderThanDays")).intValue() : null;
        Long channelId = body.containsKey("channelId") ? ((Number) body.get("channelId")).longValue() : null;
        int count = systemConfigService.cleanupCache(olderThanDays, channelId);
        return ResponseEntity.ok(Map.of("message", "已清理 " + count + " 个缓存文件", "count", count));
    }

    @DeleteMapping("/cleanup/all")
    public ResponseEntity<?> cleanupAll(@RequestBody Map<String, Object> body) {
        Integer olderThanDays = body.containsKey("olderThanDays") ? ((Number) body.get("olderThanDays")).intValue() : null;
        Map<String, Object> result = systemConfigService.cleanupAll(olderThanDays);
        return ResponseEntity.ok(Map.of("message", "清理完成", "details", result));
    }

    @GetMapping("/cleanup/config")
    public ResponseEntity<?> getCleanupConfig() {
        return ResponseEntity.ok(systemConfigService.getCleanupConfig());
    }

    @PutMapping("/cleanup/config")
    public ResponseEntity<?> updateCleanupConfig(@RequestBody Map<String, Object> body) {
        systemConfigService.setCleanupConfig(body);
        return ResponseEntity.ok(Map.of("message", "定时清理配置已保存", "config", systemConfigService.getCleanupConfig()));
    }

    @GetMapping("/channels")
    public ResponseEntity<?> listChannels() {
        return ResponseEntity.ok(channelService.listChannels(SecurityConfig.getCurrentUserId()));
    }

    @PostMapping("/channels")
    public ResponseEntity<?> createChannel(@RequestBody Map<String, String> body) {
        Long creatorId = SecurityConfig.getCurrentUserId();
        String name = body.get("name");
        String description = body.getOrDefault("description", "");
        String password = body.getOrDefault("password", null);
        String joinPermission = body.getOrDefault("joinPermission", null);

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "频道名称不能为空"));
        }

        Channel channel = channelService.createChannel(name, description, password, joinPermission, creatorId);
        return ResponseEntity.ok(channel);
    }

    @DeleteMapping("/channels/{id}")
    public ResponseEntity<?> deleteChannel(@PathVariable Long id) {
        Long operatorUserId = SecurityConfig.getCurrentUserId();
        channelService.deleteChannel(id, operatorUserId);
        return ResponseEntity.ok(Map.of("message", "频道已删除"));
    }
}
