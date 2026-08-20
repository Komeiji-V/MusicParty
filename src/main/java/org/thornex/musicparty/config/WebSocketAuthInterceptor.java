package org.thornex.musicparty.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.service.ChannelAccessService;
import org.thornex.musicparty.util.JwtUtil;

@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ChannelAccessService channelAccessService;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil,
                                    UserRepository userRepository,
                                    ChannelAccessService channelAccessService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.channelAccessService = channelAccessService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authToken = accessor.getFirstNativeHeader("auth-token");
            String channelIdStr = accessor.getFirstNativeHeader("channel-id");

            Long userId = resolveUserId(authToken);
            if (userId == null) {
                log.warn("WebSocket CONNECT rejected: invalid auth token. Session: {}", accessor.getSessionId());
                throw new MessageDeliveryException("AUTH_REQUIRED");
            }

            Long channelId = parseChannelId(channelIdStr);
            if (!channelAccessService.hasAccess(userId, channelId)) {
                log.warn("WebSocket CONNECT rejected: no access to channel {}. Session: {}", channelId, accessor.getSessionId());
                throw new MessageDeliveryException("CHANNEL_ACCESS_DENIED");
            }

            String username = userRepository.findById(userId).map(User::getUsername).orElse(null);
            accessor.getSessionAttributes().put("userId", userId);
            accessor.getSessionAttributes().put("username", username);
            // 设置 Principal（sessionId），使 /user/queue/** 用户目的地可正确解析路由
            accessor.setUser(() -> accessor.getSessionId());
            log.info("WebSocket Authenticated: Session {}, UserId {}, ChannelId {}", accessor.getSessionId(), userId, channelId);
        }

        // 订阅鉴权：频道级 topic 必须拥有对应频道的访问权限，防止越权订阅窃听私密频道
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/channel/")) {
                Long userId = accessor.getSessionAttributes() != null
                        ? (Long) accessor.getSessionAttributes().get("userId") : null;
                Long channelId = parseChannelIdFromDestination(destination);
                if (userId == null || channelId == null || !channelAccessService.hasAccess(userId, channelId)) {
                    log.warn("WebSocket SUBSCRIBE rejected: no access to destination {} (session {})",
                            destination, accessor.getSessionId());
                    throw new MessageDeliveryException("CHANNEL_ACCESS_DENIED");
                }
            }
        }
        return message;
    }

    private Long parseChannelIdFromDestination(String destination) {
        // 形如 /topic/channel/{id}/player/state
        String prefix = "/topic/channel/";
        if (!destination.startsWith(prefix)) return null;
        String rest = destination.substring(prefix.length());
        int slash = rest.indexOf('/');
        String idPart = slash > 0 ? rest.substring(0, slash) : rest;
        try {
            return Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolveUserId(String authToken) {
        if (authToken == null || authToken.isEmpty()) return null;
        try {
            Long uid = jwtUtil.getUidFromToken(authToken);
            User user = uid != null ? userRepository.findByAuthUid(uid).orElse(null) : null;
            return user != null ? user.getId() : null;
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return null;
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
