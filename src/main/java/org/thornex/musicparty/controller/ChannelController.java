package org.thornex.musicparty.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thornex.musicparty.config.SecurityConfig;
import org.thornex.musicparty.entity.Channel;
import org.thornex.musicparty.service.ChannelService;
import org.thornex.musicparty.service.MusicPlayerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final MusicPlayerService musicPlayerService;

    @PostMapping
    @PreAuthorize("isAuthenticated() and !hasRole('GUEST')")
    public ResponseEntity<?> createChannel(@RequestBody Map<String, String> body) {
        Long userId = SecurityConfig.getCurrentUserId();
        String name = body.get("name");
        String description = body.getOrDefault("description", "");
        String password = body.getOrDefault("password", null);
        String joinPermission = body.getOrDefault("joinPermission", null);

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "频道名称不能为空"));
        }

        Channel channel = channelService.createChannel(name, description, password, joinPermission, userId);
        return ResponseEntity.ok(channel);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listChannels() {
        return ResponseEntity.ok(channelService.listChannels(SecurityConfig.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getChannel(@PathVariable Long id) {
        Long userId = SecurityConfig.getCurrentUserId();
        // M4：HIDDEN 频道仅成员/管理员可见，非成员返回 404 不暴露存在性
        if (!channelService.isChannelVisibleToUser(id, userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "频道不存在"));
        }
        Channel channel = channelService.getChannel(id);
        Map<String, Object> result = new HashMap<>();
        result.put("id", channel.getId());
        result.put("name", channel.getName());
        result.put("description", channel.getDescription());
        result.put("isPublic", channel.isPublic());
        result.put("hasPassword", channel.getPasswordHash() != null && !channel.getPasswordHash().isEmpty());
        result.put("joinPermission", channel.getJoinPermission() != null ? channel.getJoinPermission().name() : "PUBLIC");
        result.put("creatorId", channel.getCreatorId());
        result.put("onlineCount", channelService.getOnlineCount(id));
        result.put("createdAt", channel.getCreatedAt());
        result.put("updatedAt", channel.getUpdatedAt());
        result.put("isChannelAdmin", channelService.isChannelAdmin(id, userId));
        result.put("isMember", channelService.isChannelMember(id, userId));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateChannel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = SecurityConfig.getCurrentUserId();
        Channel result = channelService.updateChannel(id, body, userId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteChannel(@PathVariable Long id) {
        Long userId = SecurityConfig.getCurrentUserId();
        channelService.deleteChannel(id, userId);
        return ResponseEntity.ok(Map.of("message", "频道已删除"));
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> joinChannel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = SecurityConfig.getCurrentUserId();
        String password = body.getOrDefault("password", null);
        Map<String, Object> result = channelService.joinChannel(id, userId, password);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(403).body(result);
    }

    @PostMapping("/{id}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> leaveChannel(@PathVariable Long id) {
        Long userId = SecurityConfig.getCurrentUserId();
        channelService.leaveChannel(id, userId);
        return ResponseEntity.ok(Map.of("message", "已离开频道"));
    }

    @GetMapping("/{id}/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getChannelConfig(@PathVariable Long id, @RequestParam(required = false) String key) {
        Long userId = SecurityConfig.getCurrentUserId();
        // 完整配置（含各平台 Cookie/成员/管理员列表）仅限频道管理员或超级管理员查看
        if (!channelService.isChannelAdmin(id, userId)) {
            return ResponseEntity.status(403).body(Map.of("message", "权限不足：需要频道管理员或超级管理员权限"));
        }
        if (key == null || key.isEmpty()) {
            return ResponseEntity.ok(channelService.getChannelFullConfig(id));
        }
        String value = channelService.getChannelConfig(id, key);
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @PostMapping("/{id}/sources")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> setChannelSource(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = SecurityConfig.getCurrentUserId();
        String platform = body.get("platform") != null ? body.get("platform").toString() : null;
        boolean enabled = body.get("enabled") instanceof Boolean b
                ? b : Boolean.parseBoolean(String.valueOf(body.get("enabled")));

        if (platform == null || platform.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "平台不能为空"));
        }

        channelService.setChannelSource(id, platform, enabled, userId);
        // 广播最新播放器状态（含频道音源开关），前端搜索弹窗/音源按钮实时联动
        musicPlayerService.broadcastFullPlayerState(id);
        return ResponseEntity.ok(Map.of("message", "音源状态已更新"));
    }

    @PutMapping("/{id}/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> setChannelConfig(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = SecurityConfig.getCurrentUserId();
        String key = body.get("key");
        String value = body.get("value");

        if (key == null || key.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "配置键不能为空"));
        }

        channelService.setChannelConfig(id, key, value, userId);
        return ResponseEntity.ok(Map.of("message", "配置已更新"));
    }

    @PostMapping("/{id}/admins")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addChannelAdmin(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long operatorUserId = SecurityConfig.getCurrentUserId();

        if (body.get("userId") != null) {
            Long targetUserId = ((Number) body.get("userId")).longValue();
            channelService.addChannelAdmin(id, targetUserId, operatorUserId);
        } else if (body.get("username") != null && !body.get("username").toString().isEmpty()) {
            channelService.addChannelAdminByUsername(id, body.get("username").toString(), operatorUserId);
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "用户ID或用户名不能为空"));
        }

        return ResponseEntity.ok(Map.of("message", "频道管理员已添加"));
    }

    @DeleteMapping("/{id}/admins/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeChannelAdmin(@PathVariable Long id, @PathVariable Long userId) {
        Long operatorUserId = SecurityConfig.getCurrentUserId();
        channelService.removeChannelAdmin(id, userId, operatorUserId);
        return ResponseEntity.ok(Map.of("message", "频道管理员已移除"));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addChannelMember(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long operatorUserId = SecurityConfig.getCurrentUserId();

        if (body.get("userId") != null) {
            Long targetUserId = ((Number) body.get("userId")).longValue();
            channelService.addChannelMember(id, targetUserId, operatorUserId);
        } else if (body.get("username") != null && !body.get("username").toString().isEmpty()) {
            channelService.addChannelMemberByUsername(id, body.get("username").toString(), operatorUserId);
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "用户ID或用户名不能为空"));
        }

        return ResponseEntity.ok(Map.of("message", "频道成员已添加"));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeChannelMember(@PathVariable Long id, @PathVariable Long userId) {
        Long operatorUserId = SecurityConfig.getCurrentUserId();
        channelService.removeChannelMember(id, userId, operatorUserId);
        return ResponseEntity.ok(Map.of("message", "频道成员已移除"));
    }
}
