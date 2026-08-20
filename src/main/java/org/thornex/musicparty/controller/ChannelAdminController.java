package org.thornex.musicparty.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thornex.musicparty.config.SecurityConfig;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.dto.AdminConfigUpdateRequest;
import org.thornex.musicparty.dto.PlayerState;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.service.ChannelAccessService;
import org.thornex.musicparty.service.ChannelService;
import org.thornex.musicparty.service.ChannelSessionManager;
import org.thornex.musicparty.service.MusicPlayerService;
import org.thornex.musicparty.service.stream.LiveStreamService;
import org.thornex.musicparty.service.stream.StreamTokenService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 频道管理面板 API（频道内弹窗式管理）。
 * 仅限当前频道的管理员（或总管理员）调用，作用于单个频道。
 */
@RestController
@RequestMapping("/api/channels/{id}/admin")
@RequiredArgsConstructor
@Slf4j
public class ChannelAdminController {

    private final MusicPlayerService musicPlayerService;
    private final ChannelService channelService;
    private final LiveStreamService liveStreamService;
    private final StreamTokenService streamTokenService;
    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final ChannelSessionManager channelSessionManager;
    private final ChannelAccessService channelAccessService;
    private final SimpMessagingTemplate messagingTemplate;
    private final org.thornex.musicparty.service.CookiePoolService cookiePoolService;
    private final org.thornex.musicparty.repository.CookiePoolItemRepository cookiePoolItemRepository;

    private boolean isChannelAdmin(Long channelId) {
        Long userId = SecurityConfig.getCurrentUserId();
        return userId != null && channelService.isChannelAdmin(channelId, userId);
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("message", "权限不足：需要频道管理员或超级管理员权限"));
    }

    /** 面板初始状态（锁定/投票切歌/直播流） */
    @GetMapping("/state")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> state(@PathVariable Long id) {
        if (!isChannelAdmin(id)) return forbidden();
        PlayerState st = musicPlayerService.getCurrentPlayerState(id);
        Map<String, Object> result = new HashMap<>();
        result.put("isPauseLocked", st.isPauseLocked());
        result.put("isSkipLocked", st.isSkipLocked());
        result.put("isPlayModeLocked", st.isPlayModeLocked());
        result.put("isVoteSkipEnabled", st.isVoteSkipEnabled());
        result.put("voteSkipThreshold", st.voteSkipThreshold());
        result.put("voteSkipWaitTime", st.voteSkipWaitTime());
        result.put("streamEnabled", st.isStreamEnabled());
        result.put("streamListenerCount", st.streamListenerCount());
        result.put("isFairShuffle", st.isFairShuffle());
        return ResponseEntity.ok(result);
    }

    /** 锁定/解锁 暂停、切歌、播放模式 */
    @PutMapping("/locks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> locks(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!isChannelAdmin(id)) return forbidden();
        if (body.get("all") instanceof Boolean all) {
            musicPlayerService.setAllLocks(all, id);
            return ResponseEntity.ok(Map.of("message", all ? "已锁定所有控制" : "已解锁所有控制"));
        }
        if (body.get("pause") instanceof Boolean pause) {
            musicPlayerService.setLock("PAUSE", pause, id);
        }
        if (body.get("skip") instanceof Boolean skip) {
            musicPlayerService.setLock("SKIP", skip, id);
        }
        if (body.get("playMode") instanceof Boolean playMode) {
            musicPlayerService.setLock("SHUFFLE", playMode, id);
        }
        return ResponseEntity.ok(Map.of("message", "锁定状态已更新"));
    }

    /** 投票切歌配置 */
    @PutMapping("/vote-skip")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> voteSkip(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!isChannelAdmin(id)) return forbidden();
        AdminConfigUpdateRequest req = new AdminConfigUpdateRequest(
                null, null, null, null, null, null, null, null, null,
                body.get("enabled") instanceof Boolean b ? b : null,
                body.get("threshold") instanceof Number n ? n.doubleValue() : null,
                body.get("waitTime") instanceof Number n ? n.intValue() : null
        );
        musicPlayerService.updateConfig(req, id);
        return ResponseEntity.ok(Map.of("message", "投票切歌配置已更新"));
    }

    /** 清空队列 */
    @PostMapping("/queue/clear")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> clearQueue(@PathVariable Long id) {
        if (!isChannelAdmin(id)) return forbidden();
        musicPlayerService.clearQueue(id);
        return ResponseEntity.ok(Map.of("message", "队列已清空"));
    }

    /** 清理离线成员的点播 */
    @PostMapping("/queue/clear-offline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> clearOffline(@PathVariable Long id) {
        if (!isChannelAdmin(id)) return forbidden();
        int count = musicPlayerService.clearOfflineSongs(id);
        return ResponseEntity.ok(Map.of("message", "已清理 " + count + " 首离线点播"));
    }

    /** 重置系统（清空队列与播放状态） */
    @PostMapping("/reset")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> reset(@PathVariable Long id) {
        if (!isChannelAdmin(id)) return forbidden();
        musicPlayerService.resetSystem(id);
        return ResponseEntity.ok(Map.of("message", "系统已重置"));
    }

    /** 直播流开关 */
    @PutMapping("/stream")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> streamToggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!isChannelAdmin(id)) return forbidden();
        boolean enabled = body.get("enabled") instanceof Boolean b && b;
        liveStreamService.setEnabled(enabled);
        return ResponseEntity.ok(Map.of("message", enabled ? "直播流已开启" : "直播流已关闭"));
    }

    /** 公平随机播放开关 */
    @PutMapping("/fair-shuffle")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> fairShuffle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!isChannelAdmin(id)) return forbidden();
        boolean enabled = body.get("enabled") instanceof Boolean b && b;
        musicPlayerService.setFairShuffle(enabled, id);
        return ResponseEntity.ok(Map.of("message", enabled ? "公平随机已开启" : "公平随机已关闭"));
    }

    /** 强制切歌（无视投票切歌与锁定） */
    @PostMapping("/force-skip")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> forceSkip(@PathVariable Long id) {
        if (!isChannelAdmin(id)) return forbidden();
        musicPlayerService.forceSkip(id);
        return ResponseEntity.ok(Map.of("message", "已强制切歌"));
    }

    /** 踢出用户（管理员不可被踢） */
    @PostMapping("/kick")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> kick(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!isChannelAdmin(id)) return forbidden();
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名不能为空"));
        }
        var targetOpt = userRepository.findByUsername(username);
        if (targetOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var target = targetOpt.get();
        // 管理员不可被踢（含总管理员与其他频道管理员）
        if (channelService.isChannelAdmin(id, target.getId())) {
            return ResponseEntity.status(403).body(Map.of("message", "不能踢出频道管理员"));
        }
        // 找到该用户在当前频道的全部会话并移除
        java.util.List<String> sessions = channelSessionManager.getUserSessions(id, target.getId());
        for (String sessionId : sessions) {
            channelSessionManager.removeSession(sessionId);
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/events",
                    new org.thornex.musicparty.dto.PlayerEvent(
                            "WARN", "KICKED", "SYSTEM",
                            "你已被管理员移出频道", null),
                    createSessionHeaders(sessionId));
        }
        // 撤销频道访问授权，防止其重连回来
        channelAccessService.revokeAccess(target.getId(), id);
        log.info("User {} kicked from channel {} by admin", username, id);
        return ResponseEntity.ok(Map.of("message", "已踢出 " + username + "（" + sessions.size() + " 个连接）"));
    }

    private org.springframework.messaging.MessageHeaders createSessionHeaders(String sessionId) {
        org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor =
                org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
                        org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }

    /** 本频道的 Cookie 池（各平台条目 + 该频道选中的 Cookie） */
    @GetMapping("/cookies/pool")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cookiesPool(@PathVariable Long id) {
        if (!isChannelAdmin(id)) return forbidden();
        List<Map<String, Object>> result = List.of("netease", "qq", "kugou", "bilibili").stream()
                .map(p -> {
                    Map<String, Object> group = new HashMap<>();
                    group.put("platform", p);
                    group.put("selectedId", cookiePoolService.getSelectedId(id, p));
                    group.put("items", cookiePoolService.list(p).stream()
                            .map(i -> Map.<String, Object>ofEntries(
                                    Map.entry("id", i.getId()),
                                    Map.entry("cookie", maskCookie(i.getCookie())),
                                    Map.entry("enabled", i.isEnabled()),
                                    Map.entry("selected", i.getId() != null && i.getId().equals(cookiePoolService.getSelectedId(id, p))),
                                    Map.entry("failCount", i.getFailCount()),
                                    Map.entry("errorMark", i.isErrorMark()),
                                    Map.entry("errorReason", i.getErrorReason() != null ? i.getErrorReason() : ""),
                                    Map.entry("vipType", i.getVipType()),
                                    Map.entry("submittedBy", resolveSubmitter(i.getAddedBy())),
                                    Map.entry("createdAt", i.getCreatedAt() != null ? i.getCreatedAt().toString() : "")))
                            .toList());
                    return group;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    /** 本频道手动选择使用的 Cookie；body 传 {platform, id}，id 为空/0 表示取消选择恢复自动 */
    @PutMapping("/cookies/select")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> selectCookie(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!isChannelAdmin(id)) return forbidden();
        String platform = body.get("platform") != null ? String.valueOf(body.get("platform")) : null;
        Object idObj = body.get("id");
        if (platform == null || platform.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "平台不能为空"));
        }
        if (idObj == null || String.valueOf(idObj).isBlank() || "0".equals(String.valueOf(idObj))) {
            cookiePoolService.unselect(id, platform);
            return ResponseEntity.ok(Map.of("message", "已取消选择，恢复自动轮询"));
        }
        Long cookieId = Long.valueOf(String.valueOf(idObj));
        boolean ok = cookiePoolService.select(id, platform, cookieId);
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("message", "该 Cookie 已禁用，请在总后台启用后再选择"));
        }
        return ResponseEntity.ok(Map.of("message", "已选择该 Cookie 作为本频道当前使用"));
    }


    private String maskCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) return "";
        if (cookie.length() <= 8) return cookie.charAt(0) + "****";
        return cookie.substring(0, 4) + "****" + cookie.substring(cookie.length() - 4);
    }

    private String resolveSubmitter(Long userId) {
        if (userId == null) return "";
        return userRepository.findById(userId)
                .map(org.thornex.musicparty.entity.User::getUsername)
                .orElse("");
    }
}
