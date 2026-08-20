package org.thornex.musicparty.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.thornex.musicparty.dto.*;
import org.thornex.musicparty.enums.MessageType;
import org.thornex.musicparty.service.ChannelSessionManager;
import org.thornex.musicparty.service.ChatService;
import org.thornex.musicparty.service.MusicPlayerService;
import org.thornex.musicparty.service.UserService;

import java.util.List;

@Slf4j
@Controller
public class MusicSocketController {

    private final MusicPlayerService musicPlayerService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    private final ChannelSessionManager channelSessionManager;
    private final org.thornex.musicparty.service.ChannelAccessService channelAccessService;

    public MusicSocketController(MusicPlayerService musicPlayerService, UserService userService,
                                 SimpMessagingTemplate messagingTemplate, ChatService chatService,
                                 ChannelSessionManager channelSessionManager,
                                 org.thornex.musicparty.service.ChannelAccessService channelAccessService) {
        this.musicPlayerService = musicPlayerService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
        this.channelSessionManager = channelSessionManager;
        this.channelAccessService = channelAccessService;
    }

    @MessageMapping("/channel/switch")
    public void switchChannel(@Payload ChannelSwitchRequest request,
                              SimpMessageHeaderAccessor headerAccessor,
                              @Header("simpSessionId") String sessionId) {
        Long newChannelId = request.channelId();
        if (newChannelId == null || newChannelId <= 0) return;
        Long oldChannelId = channelSessionManager.getChannelId(sessionId);
        if (oldChannelId.equals(newChannelId)) return;

        // 关键：切换频道前必须校验目标频道的访问权限（与 CONNECT 时的校验一致），
        // 防止通过 /channel/switch 绕过密码/邀请制/仅成员可见频道的权限检查。
        Long userId = headerAccessor.getSessionAttributes() != null
                ? (Long) headerAccessor.getSessionAttributes().get("userId") : null;
        if (userId == null || !channelAccessService.hasAccess(userId, newChannelId)) {
            log.warn("Channel switch rejected: session {} has no access to channel {}", sessionId, newChannelId);
            PlayerEvent deniedEvent = new PlayerEvent(
                    "ERROR", "CHANNEL_ACCESS_DENIED", "SYSTEM",
                    "无权限进入该频道，请先在首页申请加入", null);
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/events", deniedEvent,
                    createSessionHeaders(sessionId));
            return;
        }

        channelSessionManager.switchChannel(sessionId, newChannelId);
        musicPlayerService.broadcastOnlineUsers(oldChannelId);
        musicPlayerService.broadcastOnlineUsers(newChannelId);
        UserSummary summary = userService.getUser(sessionId)
                .map(u -> new UserSummary(u.getToken(), u.getSessionId(), u.getName(), u.isGuest()))
                .orElse(new UserSummary(sessionId, sessionId, "Unknown", true));
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/channel/switched", summary, createSessionHeaders(sessionId));
    }

    @MessageMapping("/player/resync")
    public void requestResync(@Header("simpSessionId") String sessionId) {
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.broadcastFullPlayerState(channelId);
    }

    @MessageMapping("/enqueue")
    public void enqueue(EnqueueRequest request, @Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.enqueue(request, sessionId, channelId);
    }

    @MessageMapping("/enqueue/playlist")
    public void enqueuePlaylist(EnqueuePlaylistRequest request, @Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.enqueuePlaylist(request, sessionId, channelId);
    }

    @MessageMapping("/control/next")
    public void nextSong(@Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.skipToNext(sessionId, channelId);
    }

    @MessageMapping("/control/toggle-shuffle")
    public void cyclePlayMode(@Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.cyclePlayMode(sessionId, channelId);
    }

    @MessageMapping("/control/toggle-pause")
    public void togglePause(@Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.togglePause(sessionId, channelId);
    }

    @MessageMapping("/queue/top")
    public void topSong(@Payload QueueActionRequest request, @Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.topSong(request.queueId(), sessionId, channelId);
    }

    @MessageMapping("/queue/remove")
    public void removeSong(@Payload QueueActionRequest request, @Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.removeSongFromQueue(request.queueId(), sessionId, channelId);
    }

    @MessageMapping("/queue/clear-mine")
    public void clearMySongs(@Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.clearMySongs(sessionId, channelId);
    }

    @MessageMapping("/control/like")
    public void likeSong(@Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        Long channelId = channelSessionManager.getChannelId(sessionId);
        musicPlayerService.likeSong(sessionId, channelId);
    }

    @MessageMapping("/user/rename")
    public void rename(RenameRequest request, @Header("simpSessionId") String sessionId) {
        if (userService.renameUser(sessionId, request.newName())) {
            Long channelId = channelSessionManager.getChannelId(sessionId);
            musicPlayerService.broadcastOnlineUsers(channelId);
            userService.getUser(sessionId).ifPresent(user -> {
                UserSummary summary = new UserSummary(user.getToken(), user.getSessionId(), user.getName(), user.isGuest());
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/me", summary, createSessionHeaders(sessionId));
            });
        } else {
            userService.getUser(sessionId).ifPresent(user -> {
                PlayerEvent errorEvent = new PlayerEvent("ERROR", "RENAME_FAILED", user.getToken(), "该名称已被占用或包含非法字符，请更换。", null);
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/events", errorEvent, createSessionHeaders(sessionId));
            });
        }
    }

    @MessageMapping("/user/bind")
    public void bindAccount(BindRequest request, @Header("simpSessionId") String sessionId) {
        userService.bindAccount(sessionId, request.platform(), request.accountId());
    }

    @SubscribeMapping("/topic/player/state")
    public PlayerState getInitialPlayerState(@Header("simpSessionId") String sessionId) {
        Long channelId = channelSessionManager.getChannelId(sessionId);
        return musicPlayerService.getCurrentPlayerState(channelId);
    }

    @SubscribeMapping("/topic/users/online")
    public List<UserSummary> getInitialOnlineUsers(@Header("simpSessionId") String sessionId) {
        Long channelId = channelSessionManager.getChannelId(sessionId);
        return userService.getOnlineUserSummaries(channelId);
    }

    @SubscribeMapping("/user/me")
    public UserSummary getMyUserInfo(@Header("simpSessionId") String sessionId) {
        return userService.getUser(sessionId)
                .map(u -> new UserSummary(u.getToken(), u.getSessionId(), u.getName(), u.isGuest()))
                .orElse(new UserSummary(sessionId, sessionId, "Unknown", true));
    }

    private boolean isGuest(String sessionId) {
        return userService.getUser(sessionId).map(User::isGuest).orElse(true);
    }

    @MessageMapping("/chat")
    public void handleChat(ChatRequest request, @Header("simpSessionId") String sessionId) {
        if (isGuest(sessionId)) return;
        if (request.content() == null || request.content().trim().isEmpty()) return;
        if (!chatService.isMessageLengthValid(request.content())) return;

        if (chatService.processIncomingMessage(sessionId, request.content().trim())) {
            return;
        }

        userService.getUser(sessionId).ifPresent(user -> {
            if (!chatService.canUserSendMessage(user.getToken())) return;

            Long channelId = channelSessionManager.getChannelId(sessionId);

            ChatMessage message = new ChatMessage(
                    java.util.UUID.randomUUID().toString(),
                    user.getToken(),
                    user.getName(),
                    request.content().trim(),
                    System.currentTimeMillis(),
                    MessageType.CHAT
            );

            chatService.addMessage(message, channelId);
            messagingTemplate.convertAndSend("/topic/channel/" + channelId + "/chat", message);
        });
    }

    @SubscribeMapping("/chat/history")
    public List<ChatMessage> getChatHistory(@Header("simpSessionId") String sessionId) {
        Long channelId = channelSessionManager.getChannelId(sessionId);
        return chatService.getHistory(0, 50, channelId);
    }

    @MessageMapping("/chat/history/fetch")
    public void fetchChatHistory(@Payload ChatHistoryFetchRequest request, @Header("simpSessionId") String sessionId) {
        Long channelId = channelSessionManager.getChannelId(sessionId);
        List<ChatMessage> history = chatService.getHistory(request.offset(), request.limit(), channelId);
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/chat/history",
                history,
                createSessionHeaders(sessionId)
        );
    }

    public record ChannelSwitchRequest(Long channelId) {}

    private MessageHeaders createSessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }
}
