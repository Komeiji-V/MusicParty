package org.thornex.musicparty.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thornex.musicparty.dto.User;
import org.thornex.musicparty.dto.UserSummary;
import org.thornex.musicparty.enums.PlayerAction;
import org.thornex.musicparty.event.SystemMessageEvent;
import org.thornex.musicparty.event.UserCountChangeEvent;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserService {

    // 主存储：Token -> User
    private final Map<String, User> usersByToken = new ConcurrentHashMap<>();

    // 辅助索引：SessionId -> Token (用于快速查找当前发消息的是谁)
    private final Map<String, String> sessionToToken = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;
    private final ChannelSessionManager channelSessionManager;
    private final org.thornex.musicparty.repository.UserRepository userRepository;
    private final org.thornex.musicparty.repository.TitleDefRepository titleDefRepository;

    // 延迟任务调度器，用于处理断连抖动
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ScheduledFuture<?>> pendingLeaveEvents = new ConcurrentHashMap<>();

    private static final long USER_EXPIRATION_MS = 1 * 60 * 60 * 1000L;
    private static final long LEAVE_DELAY_SEC = 10; // 10秒延迟判定真正离开

    public UserService(ApplicationEventPublisher eventPublisher, ChannelSessionManager channelSessionManager,
                       org.thornex.musicparty.repository.UserRepository userRepository,
                       org.thornex.musicparty.repository.TitleDefRepository titleDefRepository) {
        this.eventPublisher = eventPublisher;
        this.channelSessionManager = channelSessionManager;
        this.userRepository = userRepository;
        this.titleDefRepository = titleDefRepository;
    }

    /**
     * 处理连接
     *
     * 安全说明（H1 修复）：身份键不再接受客户端传入的 user-token（原实现可被任意伪造/冒用）。
     * 现在由服务端派生：登录用户 = "u:{userId}"（JWT 验签后的本地账号 ID），游客 = "g:{随机UUID}"。
     * 该键只作为内存中的归属/限流标识，服务端从不信任消息帧里携带的身份值，全部由 session 解析。
     *
     * @param sessionId WebSocket Session ID
     * @param userId 本地账号 ID（JWT 验签结果；WS 强制登录，正常不为 null）
     * @param nameFront 前端传来的名字（仅新用户首次建立时采用，登录用户强制用认证中心用户名）
     * @return 最终确定的 User 对象
     */
    public User handleConnect(String sessionId, Long userId, String nameFront) {
        Long channelId = channelSessionManager.getChannelId(sessionId);
        // 身份键：登录用户按 userId 派生（跨会话稳定，重连可恢复）；游客每次连接生成新键
        String identityKey = userId != null ? "u:" + userId : "g:" + UUID.randomUUID();

        // 1. 尝试找回老用户（仅登录用户可能命中：身份键稳定）
        User user = usersByToken.get(identityKey);
        if (user != null) {
            // 🟢 检查是否有待执行的“离开”任务，如果有，说明是快速重连，直接取消
            ScheduledFuture<?> pendingLeave = pendingLeaveEvents.remove(user.getToken());
            if (pendingLeave != null) {
                pendingLeave.cancel(false);
                log.info("User {} reconnected quickly, suppressed leave/join logs.", user.getName());
            } else {
                // 如果没有待执行任务，且用户之前是离线状态，则发布加入日志
                if (user.getSessionId() == null) {
                    eventPublisher.publishEvent(new SystemMessageEvent(this, SystemMessageEvent.Level.INFO, PlayerAction.USER_JOIN, user.getToken(), null, channelId));
                }
            }

            log.info("User Reconnected: {} (uid: {}) -> New Session: {}", user.getName(), userId, sessionId);
            if (user.getSessionId() != null) {
                sessionToToken.remove(user.getSessionId());
            }
            user.setSessionId(sessionId);
        }
        // 2. 新用户注册
        else {
            String initialName = StringUtils.hasText(nameFront) ? nameFront : "游客";
            initialName = deduplicateName(initialName);

            user = new User(identityKey, sessionId, initialName);
            usersByToken.put(identityKey, user);
            log.info("New User Registered: {} (identity: {}, session: {})", initialName, identityKey, sessionId);
        }

        user.setLastActiveTime(System.currentTimeMillis());
        user.setUserId(userId);
        sessionToToken.put(sessionId, user.getToken());
        eventPublisher.publishEvent(new UserCountChangeEvent(this, channelId, getOnlineUserCount(channelId)));
        return user;
    }

    public Optional<User> disconnectUser(String sessionId, Long channelId) {
        String token = sessionToToken.remove(sessionId);
        if (token == null) return Optional.empty();

        User user = usersByToken.get(token);
        if (user != null) {
            // 🟢 关键修复：多标签页支持
            // 只有当断开的 Session ID 等于用户当前的主 Session ID 时，才认为用户真的掉线了
            // 如果不等，说明用户已经连接了新的 Session (比如打开了新标签页，关闭了旧标签页)，此时忽略旧连接的断开
            if (sessionId.equals(user.getSessionId())) {
                user.setSessionId(null); // 标记离线
                user.setLastActiveTime(System.currentTimeMillis());
                log.info("User Offline (Pending Confirmation): {}", user.getName());

                // 延迟发送离开日志
                if (!user.isGuest()) {
                    String userToken = user.getToken();
                    Long leaveChannelId = channelId;
                    ScheduledFuture<?> future = scheduler.schedule(() -> {
                        pendingLeaveEvents.remove(userToken);
                        log.info("User Leave Confirmed: {}", user.getName());
                        eventPublisher.publishEvent(new SystemMessageEvent(this, SystemMessageEvent.Level.INFO, PlayerAction.USER_LEAVE, userToken, null, leaveChannelId));
                    }, LEAVE_DELAY_SEC, TimeUnit.SECONDS);
                    pendingLeaveEvents.put(userToken, future);
                }

                eventPublisher.publishEvent(new UserCountChangeEvent(this, channelId, getOnlineUserCount(channelId)));
                return Optional.of(user);
            } else {
                log.debug("Ignored disconnect for stale session {} (Current: {})", sessionId, user.getSessionId());
            }
        }
        return Optional.empty();
    }

    public Optional<User> getUserBySession(String sessionId) {
        String token = sessionToToken.get(sessionId);
        if (token == null) return Optional.empty();
        return Optional.ofNullable(usersByToken.get(token));
    }

    public Optional<User> getUser(String sessionId) {
        return getUserBySession(sessionId);
    }

    // 🟢 改名逻辑：增加查重
    public boolean renameUser(String sessionId, String newName) {
        return getUserBySession(sessionId).map(user -> {
            String rawName = newName.trim();
            // 使用一个新的变量 finalName，确保它不被修改
            String finalName = rawName.length() > 20 ? rawName.substring(0, 20) : rawName;

            if (finalName.isEmpty()) return false;

            // 禁止伪装成 游客
            if (finalName.toLowerCase().startsWith("guest") || finalName.startsWith("游客")) {
                log.warn("Rename failed: Cannot use reserved name '{}'", finalName);
                return false;
            }

            // 检查是否重名 (排除自己)
            boolean exists = usersByToken.values().stream()
                    .anyMatch(u -> u.getName().equalsIgnoreCase(finalName) && !u.getToken().equals(user.getToken()));

            if (exists) {
                log.warn("Rename failed: {} is already taken.", finalName);
                return false;
            }

            String oldName = user.getName();
            boolean wasGuest = user.isGuest();

            log.info("User Renamed: '{}' -> '{}'", oldName, finalName);
            user.setName(finalName);
            user.setGuest(false); // 改名成功，移除游客身份

            // 1. 如果是从游客变成正式用户 -> 发布加入事件
            if (wasGuest) {
                eventPublisher.publishEvent(new SystemMessageEvent(this, SystemMessageEvent.Level.INFO, PlayerAction.USER_JOIN, user.getToken(), null, channelSessionManager.getChannelId(sessionId)));
            }
            // 2. 如果是正式用户改名 -> 发布系统通知
            else if (!oldName.equals(finalName)) {
                String renameMsg = oldName + " 已更名为 " + finalName;
                eventPublisher.publishEvent(new SystemMessageEvent(this, SystemMessageEvent.Level.INFO, null, "SYSTEM", renameMsg, channelSessionManager.getChannelId(sessionId)));
            }

            return true;
        }).orElse(false);
    }

    // 辅助：名字去重
    private String deduplicateName(String name) {
        String finalName = name;
        int counter = 1;
        while (isNameTaken(finalName)) {
            finalName = name + "_" + counter++;
        }
        return finalName;
    }

    private boolean isNameTaken(String name) {
        return usersByToken.values().stream().anyMatch(u -> u.getName().equalsIgnoreCase(name));
    }

    public boolean bindAccount(String sessionId, String platform, String accountId) {
        return getUserBySession(sessionId).map(user -> {
            user.getBindings().put(platform, accountId);
            return true;
        }).orElse(false);
    }

    public List<UserSummary> getOnlineUserSummaries() {
        return usersByToken.values().stream()
                // 只返回在线用户 (sessionId != null)
                .filter(u -> u.getSessionId() != null)
                .map(u -> toSummary(u))
                .toList();
    }

    public List<UserSummary> getOnlineUserSummaries(Long channelId) {
        Set<String> channelSessions = channelSessionManager.getChannelSessions(channelId);
        return usersByToken.values().stream()
                // 只返回在线用户 (sessionId != null) 且属于该频道的会话
                .filter(u -> u.getSessionId() != null)
                .filter(u -> channelSessions.contains(u.getSessionId()))
                .map(u -> toSummary(u))
                .toList();
    }

    private UserSummary toSummary(User u) {
        String title = null;
        String color = null;
        String username = null;
        Long authUid = null;
        if (u.getUserId() != null) {
            org.thornex.musicparty.entity.User dbUser = userRepository.findById(u.getUserId()).orElse(null);
            if (dbUser != null) {
                username = dbUser.getUsername();
                authUid = dbUser.getAuthUid();
                String t = dbUser.getCurrentTitle();
                if (t != null && !t.isBlank()) {
                    title = t;
                    color = titleDefRepository.findByName(t)
                            .map(org.thornex.musicparty.entity.TitleDef::getColor)
                            .orElse("#ff5722");
                }
            }
        }
        return new UserSummary(u.getToken(), u.getSessionId(), u.getName(), u.isGuest(), username, title, color, authUid);
    }

    public int getOnlineUserCount(Long channelId) {
        return channelSessionManager.getChannelSessions(channelId).size();
    }

    /**
     * 获取最近活跃的用户 Token (包括当前在线和正在等待断连确认的用户)
     */
    public Set<String> getRecentlyActiveUserTokens() {
        return usersByToken.values().stream()
                .filter(u -> u.getSessionId() != null || pendingLeaveEvents.containsKey(u.getToken()))
                .map(User::getToken)
                .collect(Collectors.toSet());
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredUsers() {
        long now = System.currentTimeMillis();
        int initialSize = usersByToken.size();

        // removeIf 是线程安全的 (ConcurrentHashMap)
        usersByToken.entrySet().removeIf(entry -> {
            User user = entry.getValue();
            boolean isOffline = user.getSessionId() == null;
            boolean isExpired = (now - user.getLastActiveTime()) > USER_EXPIRATION_MS;

            if (isOffline && isExpired) {
                log.debug("Cleaning up expired user: {} (uid: {})", user.getName(), user.getUserId());
                return true; // 删除
            }
            return false; // 保留
        });

        int finalSize = usersByToken.size();
        if (initialSize != finalSize) {
            log.info("Cleanup Complete. Removed {} expired users. Current memory users: {}", (initialSize - finalSize), finalSize);
        }
    }

    public Optional<User> getUserByToken(String token) {
        return Optional.ofNullable(usersByToken.get(token));
    }

    /**
     * 某本地账号当前在线会话所在的全部频道（用于称号等资料变更后实时广播刷新）。
     */
    public List<Long> getOnlineChannelsOfUser(Long userId) {
        if (userId == null) return List.of();
        return usersByToken.values().stream()
                .filter(u -> userId.equals(u.getUserId()) && u.getSessionId() != null)
                .map(u -> channelSessionManager.getChannelId(u.getSessionId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}