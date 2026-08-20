package org.thornex.musicparty.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thornex.musicparty.config.SecurityConfig;
import org.thornex.musicparty.entity.Channel;
import org.thornex.musicparty.entity.Channel.JoinPermission;
import org.thornex.musicparty.entity.ChannelAdmin;
import org.thornex.musicparty.entity.ChannelConfig;
import org.thornex.musicparty.entity.ChannelMember;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.repository.ChannelAdminRepository;
import org.thornex.musicparty.repository.ChannelConfigRepository;
import org.thornex.musicparty.repository.ChannelMemberRepository;
import org.thornex.musicparty.repository.ChannelRepository;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.service.api.MusicProvider;
import org.thornex.musicparty.service.api.MusicProviderFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelAdminRepository channelAdminRepository;
    private final ChannelConfigRepository channelConfigRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MusicProviderFactory musicProviderFactory;
    private final ChannelAccessService channelAccessService;
    private final ChannelSessionManager channelSessionManager;
    private final org.thornex.musicparty.util.CryptoUtil crypto;

    private final Map<Long, Set<Long>> channelMembers = new ConcurrentHashMap<>();

    // L1：密码 join 失败限流（按 频道+用户 计数，窗口 60 秒内 10 次失败后拒绝）
    private static final int JOIN_FAIL_MAX = 10;
    private static final long JOIN_FAIL_WINDOW_MS = 60_000L;
    private final Map<String, java.util.ArrayDeque<Long>> joinFailures = new ConcurrentHashMap<>();

    @Transactional
    public Channel createChannel(String name, String description, String password, String joinPermission, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // L1：密码最短长度（防弱口令暴力枚举面）
        if (password != null && !password.isEmpty() && password.length() < 4) {
            throw new RuntimeException("频道密码至少 4 位");
        }

        JoinPermission permission = resolveJoinPermission(joinPermission, password);
        boolean hasPassword = password != null && !password.isEmpty();

        Channel channel = Channel.builder()
                .name(name)
                .description(description)
                .isPublic(permission == JoinPermission.PUBLIC)
                .joinPermission(permission)
                .creatorId(creatorId)
                .build();

        if (hasPassword) {
            channel.setPasswordHash(passwordEncoder.encode(password));
        }

        channel = channelRepository.save(channel);

        ChannelAdmin admin = ChannelAdmin.builder()
                .channelId(channel.getId())
                .userId(creatorId)
                .build();
        channelAdminRepository.save(admin);

        log.info("频道创建成功: {} (ID: {}), 创建者: {}, 加入权限: {}", name, channel.getId(), creator.getUsername(), permission);
        return channel;
    }

    @Transactional
    public Channel updateChannel(Long channelId, Map<String, Object> fields, Long userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("频道不存在"));

        checkChannelAdminOrSuperAdmin(channelId, userId);

        String name = fields.get("name") != null ? fields.get("name").toString() : null;
        String description = fields.get("description") != null ? fields.get("description").toString() : null;
        boolean passwordProvided = fields.containsKey("password") && fields.get("password") != null;
        String password = passwordProvided ? fields.get("password").toString() : null;
        String joinPermission = fields.get("joinPermission") != null ? fields.get("joinPermission").toString() : null;

        if (name != null && !name.isEmpty()) {
            channel.setName(name);
        }
        if (description != null) {
            channel.setDescription(description);
        }
        if (passwordProvided) {
            if (password.isEmpty()) {
                channel.setPasswordHash(null);
            } else {
                // L1：密码最短长度
                if (password.length() < 4) {
                    throw new RuntimeException("频道密码至少 4 位");
                }
                channel.setPasswordHash(passwordEncoder.encode(password));
            }
        }
        if (joinPermission != null) {
            JoinPermission parsed = parseJoinPermission(joinPermission);
            if (parsed == null) {
                throw new RuntimeException("无效的加入权限: " + joinPermission);
            }
            channel.setJoinPermission(parsed);
        } else if (passwordProvided) {
            // 未显式指定权限时，跟随密码变化
            if (password.isEmpty()) {
                if (channel.getJoinPermission() == JoinPermission.PASSWORD) {
                    channel.setJoinPermission(JoinPermission.PUBLIC);
                }
            } else {
                channel.setJoinPermission(JoinPermission.PASSWORD);
            }
        }
        channel.setPublic(channel.getJoinPermission() == JoinPermission.PUBLIC);

        channelRepository.save(channel);
        log.info("频道更新成功: {} (ID: {}), 加入权限: {}", channel.getName(), channelId, channel.getJoinPermission());
        return channel;
    }

    @Transactional
    public void deleteChannel(Long channelId, Long userId) {
        checkChannelAdminOrSuperAdmin(channelId, userId);

        channelConfigRepository.findByChannelId(channelId)
                .forEach(c -> channelConfigRepository.delete(c));
        channelAdminRepository.findByChannelId(channelId)
                .forEach(a -> channelAdminRepository.delete(a));
        channelMemberRepository.deleteByChannelId(channelId);
        channelRepository.deleteById(channelId);
        channelMembers.remove(channelId);

        log.info("频道删除成功: ID {}", channelId);
    }

    public Map<String, Object> joinChannel(Long channelId, Long userId, String password) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("频道不存在"));

        JoinPermission permission = channel.getJoinPermission() != null
                ? channel.getJoinPermission() : JoinPermission.PUBLIC;

        boolean isAdmin = isChannelAdmin(channelId, userId);

        switch (permission) {
            case PASSWORD -> {
                // L1：先查失败限流（防止密码暴力枚举）
                if (!allowJoinAttempt(channelId, userId)) {
                    return Map.of("success", false, "message", "尝试次数过多，请稍后再试");
                }
                if (channel.getPasswordHash() != null && !channel.getPasswordHash().isEmpty()
                        && (password == null || !passwordEncoder.matches(password, channel.getPasswordHash()))) {
                    recordJoinFailure(channelId, userId);
                    return Map.of("success", false, "message", "密码错误");
                }
            }
            case INVITE_ONLY -> {
                if (!isAdmin && !channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
                    return Map.of("success", false, "message", "该频道仅限受邀成员加入");
                }
            }
            case HIDDEN -> {
                if (!isAdmin && !channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
                    return Map.of("success", false, "message", "该频道仅限成员访问");
                }
            }
            case PUBLIC -> {
                // 直接通过
            }
        }

        channelMembers.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        channelAccessService.grantAccess(userId, channelId);
        joinFailures.remove(failKey(channelId, userId)); // 加入成功，清空失败计数
        User user = userRepository.findById(userId).orElse(null);
        String username = user != null ? user.getUsername() : "未知用户";
        log.info("用户 {} 加入了频道 {}", username, channel.getName());

        return Map.of("success", true, "message", "加入成功");
    }

    /** 密码 join 失败计数（窗口滑动） */
    private boolean allowJoinAttempt(Long channelId, Long userId) {
        String key = failKey(channelId, userId);
        synchronized (joinFailures) {
            java.util.ArrayDeque<Long> q = joinFailures.computeIfAbsent(key, k -> new java.util.ArrayDeque<>());
            long now = System.currentTimeMillis();
            while (!q.isEmpty() && now - q.peekFirst() > JOIN_FAIL_WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= JOIN_FAIL_MAX) {
                return false;
            }
            return true;
        }
    }

    private void recordJoinFailure(Long channelId, Long userId) {
        String key = failKey(channelId, userId);
        synchronized (joinFailures) {
            java.util.ArrayDeque<Long> q = joinFailures.computeIfAbsent(key, k -> new java.util.ArrayDeque<>());
            q.addLast(System.currentTimeMillis());
        }
    }

    private String failKey(Long channelId, Long userId) {
        return channelId + ":" + userId;
    }

    public void leaveChannel(Long channelId, Long userId) {
        Set<Long> members = channelMembers.get(channelId);
        if (members != null) {
            members.remove(userId);
        }
        channelAccessService.revokeAccess(userId, channelId);
        User user = userRepository.findById(userId).orElse(null);
        String username = user != null ? user.getUsername() : "未知用户";
        log.info("用户 {} 离开了频道 {}", username, channelId);
    }

    public List<Map<String, Object>> listChannels(Long userId) {
        return channelRepository.findAll().stream()
                .filter(channel -> isChannelVisible(channel, userId))
                .map(channel -> {
                    int onlineCount = channelSessionManager.getOnlineUserCount(channel.getId());
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", channel.getId());
                    item.put("name", channel.getName());
                    item.put("description", channel.getDescription() != null ? channel.getDescription() : "");
                    item.put("isPublic", channel.isPublic());
                    item.put("hasPassword", channel.getPasswordHash() != null && !channel.getPasswordHash().isEmpty());
                    item.put("joinPermission", channel.getJoinPermission() != null ? channel.getJoinPermission().name() : "PUBLIC");
                    item.put("isAdmin", userId != null && isChannelAdmin(channel.getId(), userId));
                    item.put("isMember", userId != null && isChannelMember(channel.getId(), userId));
                    item.put("creatorId", channel.getCreatorId());
                    item.put("onlineCount", onlineCount);
                    item.put("createdAt", channel.getCreatedAt());
                    item.put("updatedAt", channel.getUpdatedAt());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private boolean isChannelVisible(Channel channel, Long userId) {
        if (channel.getJoinPermission() != JoinPermission.HIDDEN) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        if (isChannelAdmin(channel.getId(), userId)) {
            return true;
        }
        return isChannelMember(channel.getId(), userId);
    }

    public Channel getChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("频道不存在"));
    }

    /** M4：按当前用户校验频道可见性（HIDDEN 频道仅成员/管理员可见，INVITE_ONLY 不暴露元数据） */
    public boolean isChannelVisibleToUser(Long channelId, Long userId) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) return false;
        return isChannelVisible(channel, userId);
    }

    public String getChannelConfig(Long channelId, String key) {
        return channelConfigRepository.findByChannelIdAndConfigKey(channelId, key)
                .map(ChannelConfig::getConfigValue)
                // M3：cookie 配置加密存储，读取时解密供内部使用
                .map(v -> isCookieKey(key) ? crypto.decrypt(v) : v)
                .orElse(null);
    }

    @Transactional
    public void setChannelConfig(Long channelId, String key, String value, Long userId) {
        checkChannelAdmin(channelId, userId);
        saveChannelConfig(channelId, key, value);
        log.info("频道配置更新: 频道 {}, key={}", channelId, key);
    }

    private boolean isCookieKey(String key) {
        return key != null && key.startsWith("cookie_");
    }

    /** 掩码后的频道 Cookie（用于管理界面展示） */
    private String maskChannelCookie(Long channelId, String key) {
        String raw = getChannelConfig(channelId, key);
        return org.thornex.musicparty.util.CryptoUtil.mask(raw);
    }

    @Transactional
    public void setChannelSource(Long channelId, String platform, boolean enabled, Long userId) {
        checkChannelAdmin(channelId, userId);
        musicProviderFactory.getProvider(platform);
        saveChannelConfig(channelId, "source_" + platform + "_enabled", String.valueOf(enabled));
        log.info("频道音源开关更新: 频道 {}, platform={}, enabled={}", channelId, platform, enabled);
    }

    private void saveChannelConfig(Long channelId, String key, String value) {
        ChannelConfig config = channelConfigRepository
                .findByChannelIdAndConfigKey(channelId, key)
                .orElseGet(() -> ChannelConfig.builder()
                        .channelId(channelId)
                        .configKey(key)
                        .build());

        // M3：cookie 配置加密存储
        config.setConfigValue(isCookieKey(key) ? crypto.encrypt(value) : value);
        channelConfigRepository.save(config);
    }

    public Map<String, Object> getChannelFullConfig(Long channelId) {
        Channel channel = getChannel(channelId);

        // M3：Cookie 对频道管理员掩码展示（不泄露完整凭证）
        Map<String, Object> cookies = new HashMap<>();
        cookies.put("netease", maskChannelCookie(channelId, "cookie_netease"));
        cookies.put("qq", maskChannelCookie(channelId, "cookie_qq"));
        cookies.put("kugou", maskChannelCookie(channelId, "cookie_kugou"));
        cookies.put("bilibili", maskChannelCookie(channelId, "cookie_bilibili"));

        Map<String, Object> sources = new HashMap<>();
        for (String platform : List.of("netease", "qq", "kugou", "bilibili")) {
            String value = getChannelConfig(channelId, "source_" + platform + "_enabled");
            sources.put(platform, value == null || Boolean.parseBoolean(value));
        }

        List<Map<String, Object>> admins = channelAdminRepository.findByChannelId(channelId).stream()
                .map(admin -> {
                    User adminUser = userRepository.findById(admin.getUserId()).orElse(null);
                    return Map.<String, Object>of(
                            "id", admin.getUserId(),
                            "username", adminUser != null ? adminUser.getUsername() : "未知用户",
                            "role", adminUser != null ? adminUser.getRole().name() : ""
                    );
                })
                .collect(Collectors.toList());

        Map<String, Object> config = new HashMap<>();
        config.put("name", channel.getName());
        config.put("description", channel.getDescription() != null ? channel.getDescription() : "");
        config.put("isPublic", channel.isPublic());
        config.put("hasPassword", channel.getPasswordHash() != null && !channel.getPasswordHash().isEmpty());
        config.put("joinPermission", channel.getJoinPermission() != null ? channel.getJoinPermission().name() : "PUBLIC");
        config.put("creatorId", channel.getCreatorId());
        config.put("onlineCount", getOnlineCount(channelId));
        config.put("cookies", cookies);
        config.put("sources", sources);
        config.put("admins", admins);
        config.put("members", getChannelMembers(channelId));
        config.put("isChannelAdmin", isChannelAdmin(channelId, SecurityConfig.getCurrentUserId()));
        return config;
    }

    @Transactional
    public void addChannelAdminByUsername(Long channelId, String username, Long operatorUserId) {
        User target = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + username));
        addChannelAdmin(channelId, target.getId(), operatorUserId);
    }

    @Transactional
    public void addChannelAdmin(Long channelId, Long targetUserId, Long operatorUserId) {
        // 仅总管理员可添加/移除频道管理员（频道管理员不得私自任命管理员）
        checkSuperAdmin(operatorUserId);

        if (channelAdminRepository.findByUserIdAndChannelId(targetUserId, channelId).isPresent()) {
            throw new RuntimeException("该用户已是频道管理员");
        }

        ChannelAdmin admin = ChannelAdmin.builder()
                .channelId(channelId)
                .userId(targetUserId)
                .build();
        channelAdminRepository.save(admin);
        log.info("添加频道管理员: 频道 {}, 用户 {}", channelId, targetUserId);
    }

    @Transactional
    public void removeChannelAdmin(Long channelId, Long targetUserId, Long operatorUserId) {
        // 仅总管理员可添加/移除频道管理员
        checkSuperAdmin(operatorUserId);

        channelAdminRepository.deleteByChannelIdAndUserId(channelId, targetUserId);
        log.info("移除频道管理员: 频道 {}, 用户 {}", channelId, targetUserId);
    }

    @Transactional
    public void addChannelMemberByUsername(Long channelId, String username, Long operatorUserId) {
        User target = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + username));
        addChannelMember(channelId, target.getId(), operatorUserId);
    }

    @Transactional
    public void addChannelMember(Long channelId, Long targetUserId, Long operatorUserId) {
        checkChannelAdminOrSuperAdmin(channelId, operatorUserId);

        if (channelMemberRepository.findByChannelIdAndUserId(channelId, targetUserId).isPresent()) {
            throw new RuntimeException("该用户已是频道成员");
        }

        ChannelMember member = ChannelMember.builder()
                .channelId(channelId)
                .userId(targetUserId)
                .build();
        channelMemberRepository.save(member);
        log.info("添加频道成员: 频道 {}, 用户 {}", channelId, targetUserId);
    }

    @Transactional
    public void removeChannelMember(Long channelId, Long targetUserId, Long operatorUserId) {
        checkChannelAdminOrSuperAdmin(channelId, operatorUserId);

        channelMemberRepository.deleteByChannelIdAndUserId(channelId, targetUserId);
        log.info("移除频道成员: 频道 {}, 用户 {}", channelId, targetUserId);
    }

    public List<Map<String, Object>> getChannelMembers(Long channelId) {
        return channelMemberRepository.findByChannelId(channelId).stream()
                .map(member -> {
                    User memberUser = userRepository.findById(member.getUserId()).orElse(null);
                    return Map.<String, Object>of(
                            "id", member.getUserId(),
                            "username", memberUser != null ? memberUser.getUsername() : "未知用户"
                    );
                })
                .collect(Collectors.toList());
    }

    public boolean isChannelMember(Long channelId, Long userId) {
        return userId != null && channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
    }

    public boolean isChannelAdmin(Long channelId, Long userId) {
        String role = SecurityConfig.getCurrentUserRole();
        if ("SUPER_ADMIN".equals(role)) {
            return true;
        }

        return userId != null && channelAdminRepository.findByUserIdAndChannelId(userId, channelId).isPresent();
    }

    public String getChannelPassword(Long channelId) {
        return channelRepository.findById(channelId)
                .map(Channel::getPasswordHash)
                .orElse(null);
    }

    public int getOnlineCount(Long channelId) {
        // 统一以 WebSocket 会话为准（REST join 与 WS 断连都能正确反映在线人数）
        return channelSessionManager.getOnlineUserCount(channelId);
    }

    private JoinPermission resolveJoinPermission(String joinPermission, String password) {
        JoinPermission parsed = parseJoinPermission(joinPermission);
        if (parsed != null) {
            return parsed;
        }
        if (password != null && !password.isEmpty()) {
            return JoinPermission.PASSWORD;
        }
        return JoinPermission.PUBLIC;
    }

    private JoinPermission parseJoinPermission(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return JoinPermission.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void checkChannelAdmin(Long channelId, Long userId) {
        if (!isChannelAdmin(channelId, userId)) {
            // 越权必须返回 403（此前抛 RuntimeException 导致 500）
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "权限不足：需要频道管理员或超级管理员权限");
        }
    }

    private void checkChannelAdminOrSuperAdmin(Long channelId, Long userId) {
        if (!isChannelAdmin(channelId, userId)) {
            // 越权必须返回 403（此前抛 RuntimeException 导致 500）
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "权限不足：需要频道管理员或超级管理员权限");
        }
    }

    /** 仅总管理员（认证中心 admin 角色）可执行的操作 */
    private void checkSuperAdmin(Long userId) {
        if (!"SUPER_ADMIN".equals(SecurityConfig.getCurrentUserRole())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "权限不足：仅总管理员可执行此操作");
        }
    }
}
