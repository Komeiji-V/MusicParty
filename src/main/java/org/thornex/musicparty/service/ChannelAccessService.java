package org.thornex.musicparty.service;

import org.springframework.stereotype.Service;
import org.thornex.musicparty.entity.Channel;
import org.thornex.musicparty.entity.Channel.JoinPermission;
import org.thornex.musicparty.repository.ChannelAdminRepository;
import org.thornex.musicparty.repository.ChannelRepository;
import org.thornex.musicparty.repository.UserRepository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 频道访问授权：记录已通过 REST join 校验的用户-频道对，
 * WebSocket CONNECT 时据此校验频道访问权限（防越权）。
 */
@Service
public class ChannelAccessService {

    private final ChannelRepository channelRepository;
    private final ChannelAdminRepository channelAdminRepository;
    private final UserRepository userRepository;

    private final Map<Long, Set<Long>> grantedAccess = new ConcurrentHashMap<>();

    public ChannelAccessService(ChannelRepository channelRepository,
                                ChannelAdminRepository channelAdminRepository,
                                UserRepository userRepository) {
        this.channelRepository = channelRepository;
        this.channelAdminRepository = channelAdminRepository;
        this.userRepository = userRepository;
    }

    /** REST join 成功后调用，登记授权 */
    public void grantAccess(Long userId, Long channelId) {
        if (userId == null || channelId == null) return;
        grantedAccess.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channelId);
    }

    /** 用户离开频道时清除授权 */
    public void revokeAccess(Long userId, Long channelId) {
        Set<Long> channels = grantedAccess.get(userId);
        if (channels != null) {
            channels.remove(channelId);
        }
    }

    /** WS CONNECT 时校验：PUBLIC 直接放行；其余需已授权或频道管理员 */
    public boolean hasAccess(Long userId, Long channelId) {
        if (userId == null || channelId == null) return false;

        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) return false;

        JoinPermission permission = channel.getJoinPermission() != null
                ? channel.getJoinPermission() : JoinPermission.PUBLIC;

        if (permission == JoinPermission.PUBLIC) {
            return true;
        }

        if (isAdmin(channelId, userId)) {
            return true;
        }

        Set<Long> channels = grantedAccess.get(userId);
        return channels != null && channels.contains(channelId);
    }

    public boolean isAdmin(Long channelId, Long userId) {
        var role = userRepository.findById(userId)
                .map(u -> u.getRole().name()).orElse(null);
        if ("SUPER_ADMIN".equals(role)) return true;
        return channelAdminRepository.findByUserIdAndChannelId(userId, channelId).isPresent();
    }
}
