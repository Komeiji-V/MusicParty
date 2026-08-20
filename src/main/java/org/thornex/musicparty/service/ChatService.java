package org.thornex.musicparty.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.dto.ChatMessage;
import org.thornex.musicparty.dto.User;
import org.thornex.musicparty.enums.MessageType;
import org.thornex.musicparty.enums.PlayerAction;
import org.thornex.musicparty.event.SystemMessageEvent;
import org.thornex.musicparty.repository.ChatMessageRepository;
import org.thornex.musicparty.util.MessageFormatter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private static final Long DEFAULT_CHANNEL_ID = 1L;

    private final Map<Long, ConcurrentLinkedDeque<ChatMessage>> channelHistories = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final AppProperties appProperties;
    private final ChannelSessionManager channelSessionManager;
    private final ChatMessageRepository chatMessageRepository;

    private final Map<String, Long> lastMessageTime = new ConcurrentHashMap<>();

    public ChatService(SimpMessagingTemplate messagingTemplate, UserService userService,
                       AppProperties appProperties, ChannelSessionManager channelSessionManager,
                       ChatMessageRepository chatMessageRepository) {
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
        this.appProperties = appProperties;
        this.channelSessionManager = channelSessionManager;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostConstruct
    public void restoreFromDatabase() {
        try {
            List<org.thornex.musicparty.entity.ChatMessage> all = chatMessageRepository.findAll();
            Map<Long, List<org.thornex.musicparty.entity.ChatMessage>> byChannel = all.stream()
                    .collect(Collectors.groupingBy(org.thornex.musicparty.entity.ChatMessage::getChannelId));
            int maxSize = appProperties.getMusicApi().getChat().getMaxHistorySize();
            for (Map.Entry<Long, List<org.thornex.musicparty.entity.ChatMessage>> entry : byChannel.entrySet()) {
                List<org.thornex.musicparty.entity.ChatMessage> rows = new ArrayList<>(entry.getValue());
                rows.sort(Comparator.comparing(org.thornex.musicparty.entity.ChatMessage::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(org.thornex.musicparty.entity.ChatMessage::getId));
                List<ChatMessage> history = new ArrayList<>();
                int from = Math.max(0, rows.size() - maxSize);
                for (int i = from; i < rows.size(); i++) {
                    history.add(toDto(rows.get(i)));
                }
                restore(history, entry.getKey());
                log.info("Restored {} chat messages for channel {}", history.size(), entry.getKey());
            }
        } catch (Exception e) {
            log.error("Failed to restore chat history from database", e);
        }
    }

    private ChatMessage toDto(org.thornex.musicparty.entity.ChatMessage row) {
        long timestamp = row.getCreatedAt() != null
                ? row.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                : System.currentTimeMillis();
        MessageType type;
        try {
            type = row.getType() != null ? MessageType.valueOf(row.getType()) : MessageType.SYSTEM;
        } catch (IllegalArgumentException e) {
            type = MessageType.SYSTEM;
        }
        return new ChatMessage(
                String.valueOf(row.getId()),
                row.getUserId(),
                row.getUsername() != null ? row.getUsername() : "SYSTEM",
                row.getContent() != null ? row.getContent() : "",
                timestamp,
                type
        );
    }

    private void persistMessage(ChatMessage message, Long channelId) {
        try {
            org.thornex.musicparty.entity.ChatMessage entity = org.thornex.musicparty.entity.ChatMessage.builder()
                    .channelId(channelId)
                    .userId(message.userId())
                    .username(message.userName())
                    .content(message.content())
                    .type(message.type() != null ? message.type().name() : MessageType.SYSTEM.name())
                    .createdAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(message.timestamp()), ZoneOffset.UTC))
                    .build();
            chatMessageRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist chat message", e);
        }
    }

    private ConcurrentLinkedDeque<ChatMessage> getHistory(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        return channelHistories.computeIfAbsent(cid, k -> new ConcurrentLinkedDeque<>());
    }

    public boolean canUserSendMessage(String userToken) {
        long now = System.currentTimeMillis();
        long last = lastMessageTime.getOrDefault(userToken, 0L);
        long minInterval = appProperties.getMusicApi().getChat().getMinIntervalMs();
        if (now - last < minInterval) {
            return false;
        }
        lastMessageTime.put(userToken, now);
        return true;
    }

    public boolean isMessageLengthValid(String content) {
        return content != null && content.length() <= appProperties.getMusicApi().getChat().getMaxMessageLength();
    }

    public boolean processIncomingMessage(String sessionId, String content) {
        if (content.startsWith("//")) {
            // 聊天命令已全部取消并可视化（管理面板/按钮），不再广播给频道
            log.info("Chat command received (ignored): {} from session {}", content, sessionId);
            return true;
        }
        return false;
    }

    public void addMessage(ChatMessage message, Long channelId) {
        ConcurrentLinkedDeque<ChatMessage> history = getHistory(channelId);
        history.addLast(message);
        int maxSize = appProperties.getMusicApi().getChat().getMaxHistorySize();
        while (history.size() > maxSize) {
            history.removeFirst();
        }
        persistMessage(message, channelId);
    }

    public void addMessage(ChatMessage message) {
        addMessage(message, DEFAULT_CHANNEL_ID);
    }

    public List<ChatMessage> getHistory(int offset, int limit, Long channelId) {
        ConcurrentLinkedDeque<ChatMessage> history = getHistory(channelId);
        List<ChatMessage> snapshot = new ArrayList<>(history);
        Collections.reverse(snapshot);
        if (offset >= snapshot.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(offset + limit, snapshot.size());
        List<ChatMessage> page = snapshot.subList(offset, end);
        Collections.reverse(page);
        return page;
    }

    public List<ChatMessage> getHistory(int offset, int limit) {
        return getHistory(offset, limit, DEFAULT_CHANNEL_ID);
    }

    public List<ChatMessage> getHistoryFull(Long channelId) {
        return new ArrayList<>(getHistory(channelId));
    }

    public List<ChatMessage> getHistoryFull() {
        return getHistoryFull(DEFAULT_CHANNEL_ID);
    }

    public void restore(List<ChatMessage> loadedHistory, Long channelId) {
        ConcurrentLinkedDeque<ChatMessage> history = getHistory(channelId);
        history.clear();
        if (loadedHistory != null) {
            history.addAll(loadedHistory);
        }
    }

    public void restore(List<ChatMessage> loadedHistory) {
        restore(loadedHistory, DEFAULT_CHANNEL_ID);
    }

    public void clearHistory(Long channelId) {
        getHistory(channelId).clear();
        try {
            chatMessageRepository.deleteByChannelId(channelId);
        } catch (Exception e) {
            log.error("Failed to clear chat messages for channel {} from database", channelId, e);
        }
    }

    public void clearHistory() {
        clearHistory(DEFAULT_CHANNEL_ID);
    }

    public void clearHistoryAndNotify(Long channelId) {
        clearHistory(channelId);
        broadcastSystemMessage("聊天记录已由管理员清空", channelId);
    }

    public void clearHistoryAndNotify() {
        clearHistoryAndNotify(DEFAULT_CHANNEL_ID);
    }

    private void broadcastSystemMessage(String content, Long channelId) {
        ChatMessage sysMsg = new ChatMessage(
                UUID.randomUUID().toString(),
                "SYSTEM",
                "SYSTEM",
                content,
                System.currentTimeMillis(),
                MessageType.SYSTEM
        );
        addMessage(sysMsg, channelId);
        messagingTemplate.convertAndSend(channelTopic(channelId), sysMsg);
    }

    @EventListener
    public void onSystemEvent(SystemMessageEvent event) {
        if (event.getLevel() == SystemMessageEvent.Level.ERROR) return;

        if (event.getAction() == PlayerAction.RESET) {
            clearHistory(event.getChannelId());
        }

        String userName = "SYSTEM";
        if (!"SYSTEM".equals(event.getUserId())) {
            userName = userService.getUserByToken(event.getUserId())
                    .map(User::getName)
                    .orElse("Unknown");
        }

        String content = MessageFormatter.format(event, userName);
        MessageType type;

        if (event.getAction() == PlayerAction.LIKE) {
            type = MessageType.LIKE;
        } else if (event.getAction() == PlayerAction.PLAY_START) {
            type = MessageType.PLAY_START;
        } else {
            type = MessageType.SYSTEM;
        }

        boolean isSystemActor = "SYSTEM".equals(event.getUserId());
        String msgUserId = isSystemActor ? "SYSTEM" : event.getUserId();
        String msgUserName = isSystemActor ? "SYSTEM" : userName;

        ChatMessage sysMsg = new ChatMessage(
                UUID.randomUUID().toString(),
                msgUserId,
                msgUserName,
                content,
                System.currentTimeMillis(),
                type
        );

        addMessage(sysMsg, event.getChannelId());
        messagingTemplate.convertAndSend(channelTopic(event.getChannelId()), sysMsg);
    }

    private String channelTopic(Long channelId) {
        return "/topic/channel/" + channelId + "/chat";
    }
}
