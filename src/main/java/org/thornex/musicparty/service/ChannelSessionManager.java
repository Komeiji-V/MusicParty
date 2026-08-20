package org.thornex.musicparty.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChannelSessionManager {

    private static final Long DEFAULT_CHANNEL_ID = 1L;

    private final ConcurrentHashMap<String, Long> sessionToChannel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> channelToSessions = new ConcurrentHashMap<>();
    // sessionId -> userId（WS CONNECT 时记录，用于按用户踢出会话）
    private final ConcurrentHashMap<String, Long> sessionToUserId = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, Long userId, Long channelId) {
        if (channelId == null) channelId = DEFAULT_CHANNEL_ID;
        sessionToChannel.put(sessionId, channelId);
        if (userId != null) sessionToUserId.put(sessionId, userId);
        channelToSessions.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void removeSession(String sessionId) {
        Long channelId = sessionToChannel.remove(sessionId);
        sessionToUserId.remove(sessionId);
        if (channelId != null) {
            Set<String> sessions = channelToSessions.get(channelId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    channelToSessions.remove(channelId);
                }
            }
        }
    }

    /** 某用户在某频道的全部会话（用于踢出） */
    public java.util.List<String> getUserSessions(Long channelId, Long userId) {
        Set<String> sessions = channelToSessions.getOrDefault(channelId, java.util.Collections.emptySet());
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String sessionId : sessions) {
            if (userId.equals(sessionToUserId.get(sessionId))) {
                result.add(sessionId);
            }
        }
        return result;
    }

    public Long getChannelId(String sessionId) {
        return sessionToChannel.getOrDefault(sessionId, DEFAULT_CHANNEL_ID);
    }

    public Set<String> getChannelSessions(Long channelId) {
        if (channelId == null) channelId = DEFAULT_CHANNEL_ID;
        return channelToSessions.getOrDefault(channelId, Collections.emptySet());
    }

    public int getOnlineUserCount(Long channelId) {
        return getChannelSessions(channelId).size();
    }

    public void switchChannel(String sessionId, Long newChannelId) {
        Long userId = sessionToUserId.get(sessionId); // 切换频道时保留用户身份
        removeSession(sessionId);
        registerSession(sessionId, userId, newChannelId);
    }

    public Set<Long> getActiveChannels() {
        return Collections.unmodifiableSet(channelToSessions.keySet());
    }
}
