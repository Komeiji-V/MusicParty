package org.thornex.musicparty.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.thornex.musicparty.service.ChannelSessionManager;
import org.thornex.musicparty.service.MusicPlayerService;
import org.thornex.musicparty.service.UserService;

@Component
@Slf4j
public class WebSocketEventListener {

    private final UserService userService;
    private final MusicPlayerService musicPlayerService;
    private final ChannelSessionManager channelSessionManager;

    public WebSocketEventListener(UserService userService, MusicPlayerService musicPlayerService,
                                  ChannelSessionManager channelSessionManager) {
        this.userService = userService;
        this.musicPlayerService = musicPlayerService;
        this.channelSessionManager = channelSessionManager;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        String channelIdStr = headerAccessor.getFirstNativeHeader("channel-id");

        // 频道内名字一律使用认证中心用户名（登录后强制一致，忽略前端 user-name）
        String authUsername = headerAccessor.getSessionAttributes() != null
                ? (String) headerAccessor.getSessionAttributes().get("username") : null;

        Long channelId = parseChannelId(channelIdStr);

        log.info("WebSocket Connect Request: Session={}, Username={}, ChannelId={}", sessionId, authUsername, channelId);

        if (sessionId != null) {
            Long userId = headerAccessor.getSessionAttributes() != null
                    ? (Long) headerAccessor.getSessionAttributes().get("userId") : null;
            channelSessionManager.registerSession(sessionId, userId, channelId);
            // 身份键由服务端派生（登录用户 u:{userId}），不再信任客户端 user-token
            userService.handleConnect(sessionId, userId, authUsername);
            musicPlayerService.broadcastOnlineUsers(channelId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (sessionId != null) {
            Long channelId = channelSessionManager.getChannelId(sessionId);
            // 关键：先移除会话再发布人数事件，否则 getOnlineUserCount 仍包含刚断开的用户，
            // 最后一人离开时事件人数为 1（非 0），播放器不会进入空闲暂停
            channelSessionManager.removeSession(sessionId);
            userService.disconnectUser(sessionId, channelId);
            musicPlayerService.broadcastOnlineUsers(channelId);
        }
    }

    private Long parseChannelId(String channelIdStr) {
        if (channelIdStr != null && !channelIdStr.isEmpty()) {
            try {
                return Long.parseLong(channelIdStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid channel-id header: {}", channelIdStr);
            }
        }
        return 1L;
    }
}
