package org.thornex.musicparty.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.dto.*;
import org.thornex.musicparty.enums.CacheStatus;
import org.thornex.musicparty.enums.PlayerAction;
import org.thornex.musicparty.enums.PlayMode;
import org.thornex.musicparty.enums.QueueItemStatus;
import org.thornex.musicparty.enums.TopResult;
import org.thornex.musicparty.event.*;
import org.thornex.musicparty.exception.ApiRequestException;
import org.thornex.musicparty.service.api.BilibiliMusicProvider;
import org.thornex.musicparty.service.api.MusicProvider;
import org.thornex.musicparty.service.api.MusicProviderFactory;
import org.thornex.musicparty.service.stream.LiveStreamService;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MusicPlayerService {

    private static final Long DEFAULT_CHANNEL_ID = 1L;
    private static final long GLOBAL_COOLDOWN_MS = 1000;
    private static final long IDLE_RESET_TIMEOUT_MS = Duration.ofHours(2).toMillis();

    private final MusicProviderFactory providerFactory;
    private final UserService userService;
    private final LocalCacheService localCacheService;
    private final LiveStreamService liveStreamService;
    private final MusicQueueManager queueManager;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties appProperties;
    private final ChannelSessionManager channelSessionManager;
    private final org.thornex.musicparty.repository.LikeRecordRepository likeRecordRepository;
    private final org.thornex.musicparty.service.ChannelService channelService;

    private final AtomicBoolean isStreamActive = new AtomicBoolean(false);
    private final Map<Long, ChannelState> channelStates = new ConcurrentHashMap<>();

    public MusicPlayerService(MusicProviderFactory providerFactory, UserService userService,
                              LocalCacheService localCacheService,
                              LiveStreamService liveStreamService,
                              MusicQueueManager queueManager,
                              ApplicationEventPublisher eventPublisher,
                              AppProperties appProperties,
                              ChannelSessionManager channelSessionManager,
                              org.thornex.musicparty.repository.LikeRecordRepository likeRecordRepository,
                              org.thornex.musicparty.service.ChannelService channelService) {
        this.providerFactory = providerFactory;
        this.userService = userService;
        this.localCacheService = localCacheService;
        this.liveStreamService = liveStreamService;
        this.queueManager = queueManager;
        this.eventPublisher = eventPublisher;
        this.appProperties = appProperties;
        this.channelSessionManager = channelSessionManager;
        this.likeRecordRepository = likeRecordRepository;
        this.channelService = channelService;
    }

    @PostConstruct
    public void init() {
        log.info("MusicPlayerService initialized with {} music providers: {}",
                providerFactory.getProviderMap().size(), providerFactory.getProviderMap().keySet());
        ChannelState defaultState = getOrCreateChannelState(DEFAULT_CHANNEL_ID);
        AppProperties.PlayerConfig playerConfig = appProperties.getMusicApi().getPlayer();
        defaultState.isVoteSkipEnabled.set(playerConfig.isVoteSkipEnabled());
        defaultState.voteSkipThreshold.set(playerConfig.getVoteSkipThreshold());
        defaultState.voteSkipWaitTime.set(playerConfig.getVoteSkipWaitTime());
    }

    private ChannelState getOrCreateChannelState(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        return channelStates.computeIfAbsent(cid, k -> {
            ChannelState cs = new ChannelState();
            AppProperties.PlayerConfig playerConfig = appProperties.getMusicApi().getPlayer();
            cs.isVoteSkipEnabled.set(playerConfig.isVoteSkipEnabled());
            cs.voteSkipThreshold.set(playerConfig.getVoteSkipThreshold());
            cs.voteSkipWaitTime.set(playerConfig.getVoteSkipWaitTime());
            cs.isFairShuffle.set(true);
            return cs;
        });
    }

    @Scheduled(fixedRate = 1000)
    public void playerLoop() {
        for (Map.Entry<Long, ChannelState> entry : channelStates.entrySet()) {
            playerLoopForChannel(entry.getKey(), entry.getValue());
        }
    }

    private void playerLoopForChannel(Long channelId, ChannelState cs) {
        if (cs.isPaused.get()) {
            return;
        }
        PlayableMusic music = cs.currentMusic.get();
        if (music != null) {
            long currentPos = calculateCurrentPosition(cs);
            if (currentPos >= music.duration() && music.duration() > 0) {
                log.info("Song finished: {} (channel: {})", music.name(), channelId);
                if (cs.playMode.get() == PlayMode.REPEAT_ONE) {
                    cs.positionAnchor.set(0);
                    cs.timestampAnchor.set(System.currentTimeMillis());
                    broadcastFullPlayerState(channelId);
                    return;
                }
                Music finishedMusic = new Music(
                        music.id(), music.name(), music.artists(),
                        music.duration(), music.platform(), music.coverUrl()
                );
                queueManager.addToHistory(finishedMusic, channelId);
                cs.currentMusic.set(null);
                playNextInQueue(channelId);
            }
        } else {
            if (getChannelOnlineUsers(channelId).isEmpty() && !isStreamActive.get()) {
                return;
            }
            if (!queueManager.getQueueSnapshot(channelId).isEmpty()) {
                playNextInQueue(channelId);
            }
        }
    }

    private synchronized void playNextInQueue(Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        if (cs.currentMusic.get() != null || cs.isLoading.get()) {
            return;
        }
        Map<String, QueueItemStatus> statusMap = buildStatusMap(channelId);
        Set<String> onlineTokens = getChannelOnlineTokens(channelId);
        MusicQueueItem nextItem = queueManager.pollNext(
                cs.playMode.get(), cs.isFairShuffle.get(), cs.allowOfflineShuffle.get(),
                statusMap, onlineTokens, channelId
        );
        if (nextItem == null) {
            if (cs.isLoading.get()) {
                cs.isLoading.set(false);
            }
            broadcastFullPlayerState(channelId);
            return;
        }
        if (nextItem.status() == QueueItemStatus.FAILED ||
                (statusMap.get(nextItem.music().id()) == QueueItemStatus.FAILED)) {
            log.warn("Skipping failed song: {}", nextItem.music().name());
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.ERROR, PlayerAction.ERROR_LOAD, "SYSTEM",
                    nextItem.music().name(), channelId));
            playNextInQueue(channelId);
            return;
        }
        long currentVersion = cs.playHeadVersion.incrementAndGet();
        cs.isLoading.set(true);
        broadcastFullPlayerState(channelId);
        cs.isPaused.set(false);
        log.info("Playing next in channel {}: {}", channelId, nextItem.music().name());
        try {
            resolvePlayableMusic(nextItem, channelId)
                    .timeout(Duration.ofSeconds(10))
                    .subscribe(
                            playableMusic -> {
                                ChannelState state = getOrCreateChannelState(channelId);
                                if (state.playHeadVersion.get() == currentVersion) {
                                    applyNewSong(channelId, state, playableMusic, nextItem);
                                } else {
                                    log.info("Discarded stale play result for {}", nextItem.music().name());
                                }
                            },
                            error -> {
                                log.error("Play failed for {}: {}", nextItem.music().name(), error.getMessage());
                                eventPublisher.publishEvent(new SystemMessageEvent(this,
                                        SystemMessageEvent.Level.ERROR, PlayerAction.ERROR_LOAD, "SYSTEM",
                                        nextItem.music().name(), channelId));
                                ChannelState state = getOrCreateChannelState(channelId);
                                state.isLoading.set(false);
                                broadcastFullPlayerState(channelId);
                                playNextInQueue(channelId);
                            });
        } catch (Exception e) {
            log.error("Unexpected error in playNextInQueue", e);
            cs.isLoading.set(false);
            broadcastFullPlayerState(channelId);
        }
    }

    private Mono<PlayableMusic> resolvePlayableMusic(MusicQueueItem queueItem, Long channelId) {
        Music music = queueItem.music();
        String platform = music.platform();
        MusicProvider provider = providerFactory.getProvider(platform);
        String localUrl = "bilibili".equals(platform) ? localCacheService.getLocalUrl(music.id()) : null;
        Mono<String> urlMono = localUrl != null
                ? Mono.just(localUrl)
                : provider.getPlayUrl(music.id(), resolveQuality(platform), channelId);
        return urlMono
                .map(url -> {
                    if (url == null || url.isEmpty()) {
                        throw new ApiRequestException("Empty play URL for platform: " + platform);
                    }
                    return new PlayableMusic(
                            music.id(), music.name(), music.artists(), music.duration(),
                            platform, url, music.coverUrl(), false
                    );
                })
                .doOnError(e -> log.error("Failed to resolve play URL for {} ({})", music.name(), platform, e));
    }

    private String resolveQuality(String platform) {
        return switch (platform) {
            case "netease" -> appProperties.getMusicApi().getNetease().getQuality();
            case "qq" -> appProperties.getMusicApi().getQq().getQuality();
            default -> null;
        };
    }

    private long calculateCurrentPosition(ChannelState cs) {
        if (cs.currentMusic.get() == null) return 0;
        if (cs.isPaused.get()) {
            return cs.positionAnchor.get();
        } else {
            long now = System.currentTimeMillis();
            long elapsed = now - cs.timestampAnchor.get();
            return cs.positionAnchor.get() + elapsed;
        }
    }

    private void applyNewSong(Long channelId, ChannelState cs, PlayableMusic music, MusicQueueItem queueItem) {
        cs.likedUserIds.clear();
        cs.likeMarkers.clear();
        cs.skipVotes.clear();
        cs.currentMusic.set(music);
        cs.currentEnqueuerId.set(queueItem.enqueuedBy().token());
        cs.currentEnqueuerName.set(queueItem.enqueuedBy().name());
        cs.positionAnchor.set(0);
        cs.timestampAnchor.set(System.currentTimeMillis());
        cs.isPaused.set(false);
        log.info("Now playing in channel {}: {}", channelId, music.name());
        cs.isLoading.set(false);
        broadcastFullPlayerState(channelId);
        broadcastQueueUpdate(channelId);
        eventPublisher.publishEvent(new SystemMessageEvent(this,
                SystemMessageEvent.Level.INFO, PlayerAction.PLAY_START,
                queueItem.enqueuedBy().token(), music.name(), channelId));
    }

    public PlayerState getCurrentPlayerState(Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        PlayableMusic music = cs.currentMusic.get();
        NowPlayingInfo infoToSend = null;
        if (music != null) {
            int likeCount = 0;
            try {
                likeCount = (int) likeRecordRepository
                        .countByChannelIdAndMusicIdAndPlatform(channelId, music.id(), music.platform());
            } catch (Exception e) {
                log.error("Failed to count like records for music {} in channel {}", music.id(), channelId, e);
            }
            infoToSend = new NowPlayingInfo(
                    music,
                    calculateCurrentPosition(cs),
                    cs.currentEnqueuerId.get(),
                    cs.currentEnqueuerName.get(),
                    cs.likedUserIds,
                    cs.likeMarkers,
                    likeCount
            );
        }
        Set<String> onlineTokens = getChannelOnlineTokens(channelId);
        int currentVoteCount = (int) cs.skipVotes.stream().filter(onlineTokens::contains).count();
        int eligibleCount = calculateEligibleUsers(cs, onlineTokens);
        PlayMode currentPlayMode = cs.playMode.get();
        return new PlayerState(
                infoToSend,
                getQueueWithUpdatedStatus(channelId),
                currentPlayMode.name(),
                currentPlayMode == PlayMode.SHUFFLE,
                cs.isFairShuffle.get(),
                cs.allowOfflineShuffle.get(),
                getChannelOnlineUsers(channelId),
                cs.isPaused.get(),
                cs.isPauseLocked.get(),
                cs.isSkipLocked.get(),
                cs.isPlayModeLocked.get(),
                cs.isLoading.get(),
                liveStreamService.getStreamListenerCount(),
                liveStreamService.isEnabled(),
                cs.isVoteSkipEnabled.get(),
                cs.voteSkipThreshold.get(),
                cs.voteSkipWaitTime.get(),
                currentVoteCount,
                eligibleCount,
                new PlayerState.AppConfigSummary(
                        appProperties.getMusicApi().getQueue().getMaxSize(),
                        appProperties.getMusicApi().getQueue().getHistorySize(),
                        appProperties.getMusicApi().getQueue().getMaxUserSongs(),
                        appProperties.getMusicApi().getPlayer().getMaxPlaylistImportSize(),
                        appProperties.getMusicApi().getChat().getMaxHistorySize(),
                        appProperties.getMusicApi().getChat().getMinIntervalMs(),
                        appProperties.getMusicApi().getChat().getMaxMessageLength(),
                        appProperties.getMusicApi().getNetease().isEnabled(),
                        appProperties.getMusicApi().getBilibili().isEnabled(),
                        appProperties.getMusicApi().getQq().isEnabled(),
                        appProperties.getMusicApi().getKugou().isEnabled(),
                        cs.isVoteSkipEnabled.get(),
                        cs.voteSkipThreshold.get(),
                        cs.voteSkipWaitTime.get()
                )
        );
    }

    public PlayerState getCurrentPlayerState() {
        return getCurrentPlayerState(DEFAULT_CHANNEL_ID);
    }

    public NowPlayingInfo getNowPlayingSummary(Long channelId) {
        ChannelState cs = channelStates.get(channelId != null ? channelId : DEFAULT_CHANNEL_ID);
        if (cs == null) {
            return null;
        }
        PlayableMusic music = cs.currentMusic.get();
        if (music == null) {
            return null;
        }
        int likeCount = 0;
        try {
            likeCount = (int) likeRecordRepository
                    .countByChannelIdAndMusicIdAndPlatform(channelId, music.id(), music.platform());
        } catch (Exception e) {
            log.error("Failed to count like records for music {} in channel {}", music.id(), channelId, e);
        }
        return new NowPlayingInfo(
                music,
                calculateCurrentPosition(cs),
                cs.currentEnqueuerId.get(),
                cs.currentEnqueuerName.get(),
                cs.likedUserIds,
                cs.likeMarkers,
                likeCount
        );
    }

    public boolean isChannelPaused(Long channelId) {
        ChannelState cs = channelStates.get(channelId != null ? channelId : DEFAULT_CHANNEL_ID);
        return cs != null && cs.isPaused.get();
    }

    private int calculateEligibleUsers(ChannelState cs, Set<String> onlineTokens) {
        String enqueuerToken = cs.currentEnqueuerId.get();
        return (int) getChannelOnlineUsers(channelStates.entrySet().stream()
                .filter(e -> e.getValue() == cs)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(DEFAULT_CHANNEL_ID))
                .stream()
                .filter(u -> !u.isGuest())
                .filter(u -> onlineTokens.contains(u.token()))
                .filter(u -> !u.token().equals(enqueuerToken))
                .count();
    }

    private Long findChannelIdByState(ChannelState cs) {
        for (Map.Entry<Long, ChannelState> entry : channelStates.entrySet()) {
            if (entry.getValue() == cs) {
                return entry.getKey();
            }
        }
        return DEFAULT_CHANNEL_ID;
    }

    public void toggleFairShuffle(String sessionId, Long channelId) {
        if (isRateLimited(sessionId, channelId)) return;
        ChannelState cs = getOrCreateChannelState(channelId);
        boolean current;
        boolean newState;
        do {
            current = cs.isFairShuffle.get();
            newState = !current;
        } while (!cs.isFairShuffle.compareAndSet(current, newState));
        log.info("Fair shuffle mode set to {} by {} in channel {}", newState, getUserName(sessionId), channelId);
        broadcastFullPlayerState(channelId);
        broadcastQueueUpdate(channelId);
    }

    public void toggleFairShuffle(String sessionId) {
        toggleFairShuffle(sessionId, DEFAULT_CHANNEL_ID);
    }

    /** 管理员直接设置公平随机模式 */
    public void setFairShuffle(boolean enabled, Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        ChannelState cs = getOrCreateChannelState(cid);
        cs.isFairShuffle.set(enabled);
        log.info("Fair shuffle mode set to {} by admin in channel {}", enabled, cid);
        broadcastFullPlayerState(cid);
        broadcastQueueUpdate(cid);
    }

    public void toggleAllowOfflineShuffle(String sessionId, Long channelId) {
        if (isRateLimited(sessionId, channelId)) return;
        ChannelState cs = getOrCreateChannelState(channelId);
        boolean current;
        boolean newState;
        do {
            current = cs.allowOfflineShuffle.get();
            newState = !current;
        } while (!cs.allowOfflineShuffle.compareAndSet(current, newState));
        log.info("Allow offline shuffle set to {} by {} in channel {}", newState, getUserName(sessionId), channelId);
        broadcastFullPlayerState(channelId);
    }

    public void toggleAllowOfflineShuffle(String sessionId) {
        toggleAllowOfflineShuffle(sessionId, DEFAULT_CHANNEL_ID);
    }

    public int clearOfflineSongs(Long channelId) {
        Set<String> onlineTokens = getChannelOnlineTokens(channelId);
        List<MusicQueueItem> snapshot = queueManager.getQueueSnapshot(channelId);
        int removedCount = 0;
        for (MusicQueueItem item : snapshot) {
            if (!onlineTokens.contains(item.enqueuedBy().token())) {
                queueManager.remove(item.queueId(), channelId);
                removedCount++;
            }
        }
        log.info("Cleared {} songs from offline users in channel {}.", removedCount, channelId);
        broadcastQueueUpdate(channelId);
        eventPublisher.publishEvent(new SystemMessageEvent(this,
                SystemMessageEvent.Level.WARN, PlayerAction.SYSTEM_MESSAGE, "SYSTEM",
                "管理员已清理 " + removedCount + " 首离线成员的点播歌曲", channelId));
        return removedCount;
    }

    public int clearOfflineSongs() {
        return clearOfflineSongs(DEFAULT_CHANNEL_ID);
    }

    /**
     * 清空当前用户自己点播的歌曲（原 //clear 命令的可视化实现）
     */
    public void clearMySongs(String sessionId, Long channelId) {
        Optional<User> operator = userService.getUser(sessionId);
        if (operator.isEmpty()) return;
        User operatorUser = operator.get();
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        int count = queueManager.removeByUser(operatorUser.getToken(), cid);
        if (count > 0) {
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.INFO, PlayerAction.SYSTEM_MESSAGE,
                    operatorUser.getToken(), String.format("清空了自己点的 %d 首歌", count), cid));
            broadcastQueueUpdate(cid);
        }
    }

    public void setLock(String type, boolean locked, Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        AtomicBoolean targetLock;
        String desc;
        switch (type.toUpperCase()) {
            case "PAUSE" -> { targetLock = cs.isPauseLocked; desc = "暂停"; }
            case "SKIP" -> { targetLock = cs.isSkipLocked; desc = "切歌"; }
            case "SHUFFLE" -> { targetLock = cs.isPlayModeLocked; desc = "播放模式"; }
            default -> throw new IllegalArgumentException("Unknown lock type");
        }
        boolean old = targetLock.getAndSet(locked);
        if (old != locked) {
            log.info("{} lock set to: {} in channel {}", desc, locked, channelId);
            broadcastFullPlayerState(channelId);
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.WARN, PlayerAction.SYSTEM_MESSAGE, "SYSTEM",
                    locked ? "管理员锁定了" + desc : "管理员解锁了" + desc, channelId));
        }
    }

    public void setLock(String type, boolean locked) {
        setLock(type, locked, DEFAULT_CHANNEL_ID);
    }

    public void setAllLocks(boolean locked, Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        cs.isPauseLocked.set(locked);
        cs.isSkipLocked.set(locked);
        cs.isPlayModeLocked.set(locked);
        broadcastFullPlayerState(channelId);
        eventPublisher.publishEvent(new SystemMessageEvent(this,
                SystemMessageEvent.Level.WARN, PlayerAction.SYSTEM_MESSAGE, "SYSTEM",
                locked ? "管理员锁定了所有控制" : "管理员解锁了所有控制", channelId));
    }

    public void setAllLocks(boolean locked) {
        setAllLocks(locked, DEFAULT_CHANNEL_ID);
    }

    public void enqueue(EnqueueRequest request, String sessionId, Long channelId) {
        Optional<User> userOpt = userService.getUser(sessionId);
        if (userOpt.isEmpty()) return;
        User enqueuer = userOpt.get();

        if (!providerFactory.isProviderEnabled(request.platform())) {
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.ERROR, PlayerAction.SYSTEM_MESSAGE, enqueuer.getToken(),
                    "添加失败: 该音乐源已被禁用", channelId));
            return;
        }

        long userSongCount = queueManager.getQueueSnapshot(channelId).stream()
                .filter(item -> item.enqueuedBy().token().equals(enqueuer.getToken()))
                .count();

        if (userSongCount >= appProperties.getMusicApi().getQueue().getMaxUserSongs()) {
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.ERROR, PlayerAction.SYSTEM_MESSAGE, enqueuer.getToken(),
                    "添加失败: 您的点歌数量已达上限 (" + appProperties.getMusicApi().getQueue().getMaxUserSongs() + "首)", channelId));
            return;
        }

        MusicProvider provider = providerFactory.getProvider(request.platform());
        provider.getPlayableMusic(request.musicId())
                .subscribe(music -> {
                            QueueItemStatus initialStatus = "bilibili".equals(request.platform())
                                    ? QueueItemStatus.PENDING : QueueItemStatus.READY;
                            if (provider instanceof BilibiliMusicProvider biliProvider) {
                                biliProvider.prefetchMusic(music.id());
                            }

                            MusicQueueItem newItem = queueManager.add(music,
                                    new UserSummary(enqueuer.getToken(), enqueuer.getSessionId(),
                                            enqueuer.getName(), enqueuer.isGuest()),
                                    initialStatus, channelId);

                            if (newItem != null) {
                                log.info("{} enqueued: {} in channel {}", enqueuer.getName(), music.name(), channelId);
                                broadcastQueueUpdate(channelId);
                                eventPublisher.publishEvent(new SystemMessageEvent(this,
                                        SystemMessageEvent.Level.SUCCESS, PlayerAction.ADD,
                                        enqueuer.getToken(), music.name(), channelId));
                            }
                        },
                        error -> {
                            log.error("Enqueue failed for musicId: {}", request.musicId(), error);
                            String msg = error.getMessage() != null && error.getMessage().contains("Could not get Bilibili video info")
                                    ? "无效资源或API受限" : error.getMessage();
                            eventPublisher.publishEvent(new SystemMessageEvent(this,
                                    SystemMessageEvent.Level.ERROR, PlayerAction.SYSTEM_MESSAGE,
                                    enqueuer.getToken(), "添加失败: " + msg, channelId));
                        });
    }

    public void enqueue(EnqueueRequest request, String sessionId) {
        enqueue(request, sessionId, DEFAULT_CHANNEL_ID);
    }

    public void likeSong(String sessionId, Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        PlayableMusic music = cs.currentMusic.get();
        if (music == null) return;
        String token = getUserToken(sessionId);
        // 不能给自己点播的歌点赞
        String enqueuer = cs.currentEnqueuerId.get();
        if (enqueuer != null && enqueuer.equals(token)) {
            log.info("Like rejected: {} attempted to like own enqueued song in channel {}",
                    getUserName(sessionId), channelId);
            return;
        }
        if (cs.likedUserIds.contains(token)) return;
        cs.likedUserIds.add(token);
        long progress = calculateCurrentPosition(cs);
        cs.likeMarkers.add(progress);
        log.info("Like received from {} in channel {}", getUserName(sessionId), channelId);
        try {
            likeRecordRepository.save(org.thornex.musicparty.entity.LikeRecord.builder()
                    .channelId(channelId)
                    .musicId(music.id())
                    .platform(music.platform())
                    .musicName(music.name())
                    .artists(music.artists() != null ? String.join(",", music.artists()) : "")
                    .requesterName(cs.currentEnqueuerName.get())
                    .likerUsername(getUserName(sessionId))
                    .build());
        } catch (Exception e) {
            log.error("Failed to persist like record for music {} in channel {}", music.id(), channelId, e);
        }
        eventPublisher.publishEvent(new SystemMessageEvent(this,
                SystemMessageEvent.Level.SUCCESS, PlayerAction.LIKE, token, music.name(), channelId));
        broadcastFullPlayerState(channelId);
    }

    public void likeSong(String sessionId) {
        likeSong(sessionId, DEFAULT_CHANNEL_ID);
    }

    public void enqueuePlaylist(EnqueuePlaylistRequest request, String sessionId, Long channelId) {
        Optional<User> userOpt = userService.getUser(sessionId);
        if (userOpt.isEmpty()) return;
        User enqueuer = userOpt.get();

        if (!providerFactory.isProviderEnabled(request.platform())) {
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.ERROR, PlayerAction.SYSTEM_MESSAGE, enqueuer.getToken(),
                    "导入失败: 该音乐源已被禁用", channelId));
            return;
        }

        long currentCount = queueManager.getQueueSnapshot(channelId).stream()
                .filter(item -> item.enqueuedBy().token().equals(enqueuer.getToken()))
                .count();
        int maxUserSongs = appProperties.getMusicApi().getQueue().getMaxUserSongs();

        if (currentCount >= maxUserSongs) {
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.ERROR, PlayerAction.SYSTEM_MESSAGE, enqueuer.getToken(),
                    "导入失败: 您的点歌数量已达上限", channelId));
            return;
        }

        int remainingQuota = (int) (maxUserSongs - currentCount);
        int importLimit = Math.min(appProperties.getMusicApi().getPlayer().getMaxPlaylistImportSize(), remainingQuota);

        MusicProvider provider = providerFactory.getProvider(request.platform());
        provider.getPlaylistSongs(request.playlistId(), 0, importLimit)
                .subscribe(musics -> {
                    int count = 0;
                    QueueItemStatus initialStatus = "bilibili".equals(request.platform())
                            ? QueueItemStatus.PENDING : QueueItemStatus.READY;

                    for (Music music : musics) {
                        if (provider instanceof BilibiliMusicProvider biliProvider) {
                            biliProvider.prefetchMusic(music.id());
                        }
                        MusicQueueItem newItem = queueManager.add(music,
                                new UserSummary(enqueuer.getToken(), enqueuer.getSessionId(),
                                        enqueuer.getName(), enqueuer.isGuest()),
                                initialStatus, channelId);
                        if (newItem != null) {
                            count++;
                        }
                    }

                    log.info("{} enqueued {} songs from playlist in channel {}", enqueuer.getName(), count, channelId);
                    broadcastQueueUpdate(channelId);
                    eventPublisher.publishEvent(new SystemMessageEvent(this,
                            SystemMessageEvent.Level.SUCCESS, PlayerAction.IMPORT_PLAYLIST,
                            enqueuer.getToken(), String.valueOf(count), channelId));
                });
    }

    public void enqueuePlaylist(EnqueuePlaylistRequest request, String sessionId) {
        enqueuePlaylist(request, sessionId, DEFAULT_CHANNEL_ID);
    }

    public synchronized void topSong(String queueId, String sessionId, Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        // L2：置顶仅限自己的歌曲或频道管理员（防止任意成员反复置顶他人歌曲打断队列）
        Optional<MusicQueueItem> existing = queueManager.getItem(queueId, channelId);
        Optional<User> operator = userService.getUser(sessionId);
        if (existing.isPresent() && operator.isPresent()) {
            User op = operator.get();
            boolean isOwner = existing.get().enqueuedBy().token().equals(op.getToken());
            boolean isAdmin = channelService.isChannelAdmin(channelId, op.getUserId());
            if (!isOwner && !isAdmin) {
                log.warn("Top rejected: {} attempted to top song enqueued by {} in channel {}",
                        op.getName(), existing.get().enqueuedBy().name(), channelId);
                return;
            }
            // 权限分级：普通用户对自己已置顶的歌再点 = 取消个人置顶（不允许升级为全局置顶）；管理员可全局置顶
            if (isOwner && !isAdmin && existing.get().priority() == org.thornex.musicparty.enums.Priority.USER_TOP) {
                TopResult r = queueManager.unTop(queueId, channelId);
                if (r != TopResult.NONE) {
                    log.info("Song un-topped by {} in channel {}", op.getName(), channelId);
                    broadcastQueueUpdate(channelId);
                }
                return;
            }
        }
        TopResult result = queueManager.top(queueId, cs.playMode.get(), channelId);
        if (result != TopResult.NONE) {
            log.info("Song topped ({}) request by {} in channel {}", result, getUserName(sessionId), channelId);
            broadcastQueueUpdate(channelId);
            if (result == TopResult.GLOBAL) {
                String songName = queueManager.getItem(queueId, channelId)
                        .map(item -> item.music().name())
                        .orElse("未知歌曲");
                eventPublisher.publishEvent(new SystemMessageEvent(this,
                        SystemMessageEvent.Level.INFO, PlayerAction.TOP,
                        getUserToken(sessionId), songName, channelId));
            }
            if (cs.currentMusic.get() == null) {
                playNextInQueue(channelId);
            }
        }
    }

    public void topSong(String queueId, String sessionId) {
        topSong(queueId, sessionId, DEFAULT_CHANNEL_ID);
    }

    public void removeSongFromQueue(String queueId, String sessionId, Long channelId) {
        // 越权防护：只允许移除自己点播的歌曲（管理员可通过管理员面板清理队列）
        Optional<MusicQueueItem> existing = queueManager.getItem(queueId, channelId);
        if (existing.isEmpty()) {
            return;
        }
        Optional<User> operator = userService.getUser(sessionId);
        if (operator.isEmpty()) {
            return;
        }
        User operatorUser = operator.get();
        boolean isOwner = existing.get().enqueuedBy().token().equals(operatorUser.getToken());
        if (!isOwner) {
            log.warn("Remove rejected: {} attempted to remove song {} enqueued by {} in channel {}",
                    operatorUser.getName(), existing.get().music().name(),
                    existing.get().enqueuedBy().name(), channelId);
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.ERROR, PlayerAction.SYSTEM_MESSAGE,
                    operatorUser.getToken(), "只能移除自己点播的歌曲", channelId));
            return;
        }
        Optional<MusicQueueItem> removedItem = queueManager.remove(queueId, channelId);
        if (removedItem.isPresent()) {
            log.info("Removed song from queue by {} in channel {}", getUserName(sessionId), channelId);
            broadcastQueueUpdate(channelId);
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.INFO, PlayerAction.REMOVE,
                    getUserToken(sessionId), removedItem.get().music().name(), channelId));
        }
    }

    public void removeSongFromQueue(String queueId, String sessionId) {
        removeSongFromQueue(queueId, sessionId, DEFAULT_CHANNEL_ID);
    }

    public void skipToNext(String sessionId, Long channelId) {
        if (isRateLimited(sessionId, channelId)) return;
        ChannelState cs = getOrCreateChannelState(channelId);
        if ("SYSTEM".equals(sessionId) || !cs.isVoteSkipEnabled.get()) {
            executeSkip(sessionId, channelId);
            return;
        }
        handleVoteSkip(sessionId, channelId);
    }

    public void skipToNext(String sessionId) {
        skipToNext(sessionId, DEFAULT_CHANNEL_ID);
    }

    /**
     * 管理员强制切歌（无视投票切歌与锁定的"SYSTEM"通道）
     */
    public void forceSkip(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        log.info("Admin force skip in channel {}", cid);
        skipToNext("SYSTEM", cid);
    }

    private void handleVoteSkip(String sessionId, Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        if (cs.currentMusic.get() == null) return;
        Optional<User> userOpt = userService.getUser(sessionId);
        if (userOpt.isEmpty() || userOpt.get().isGuest()) return;
        String token = userOpt.get().getToken();
        String enqueuerToken = cs.currentEnqueuerId.get();
        if (token.equals(enqueuerToken)) {
            log.info("Enqueuer {} skipped their own song in channel {}.", userOpt.get().getName(), channelId);
            executeSkip(sessionId, channelId);
            return;
        }
        long currentPos = calculateCurrentPosition(cs);
        if (currentPos < cs.voteSkipWaitTime.get() * 1000L) {
            return;
        }
        if (cs.skipVotes.contains(token)) {
            cs.skipVotes.remove(token);
        } else {
            cs.skipVotes.add(token);
        }
        checkVoteSkipThreshold(channelId);
        broadcastFullPlayerState(channelId);
    }

    private void checkVoteSkipThreshold(Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        Set<String> onlineTokens = getChannelOnlineTokens(channelId);
        int currentVoteCount = (int) cs.skipVotes.stream().filter(onlineTokens::contains).count();
        int eligibleCount = calculateEligibleUsers(cs, onlineTokens);
        if (eligibleCount > 0 && (double) currentVoteCount / eligibleCount >= cs.voteSkipThreshold.get()) {
            String msg = String.format("投票切歌通过！(%d/%d 票)，正在进入下一首。", currentVoteCount, eligibleCount);
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.INFO, PlayerAction.SYSTEM_MESSAGE,
                    "SYSTEM", msg, channelId));
            executeSkip("SYSTEM", channelId);
        }
    }

    private void executeSkip(String sessionId, Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        if (cs.isSkipLocked.get() && !"SYSTEM".equals(sessionId)) {
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.ERROR, PlayerAction.SYSTEM_MESSAGE,
                    getUserToken(sessionId), "切歌功能已被锁定", channelId));
            return;
        }
        cs.playHeadVersion.incrementAndGet();
        cs.isLoading.set(false);
        cs.currentMusic.set(null);
        cs.positionAnchor.set(0);
        if (!"SYSTEM".equals(sessionId)) {
            eventPublisher.publishEvent(new SystemMessageEvent(this,
                    SystemMessageEvent.Level.INFO, PlayerAction.SKIP,
                    getUserToken(sessionId), null, channelId));
        }
        playNextInQueue(channelId);
    }

    public void updateConfig(AdminConfigUpdateRequest request, Long channelId) {
        StringBuilder logMsg = new StringBuilder("System configuration updated: ");

        if (request.maxSize() != null) {
            appProperties.getMusicApi().getQueue().setMaxSize(request.maxSize());
            logMsg.append("MaxQueueSize=").append(request.maxSize()).append(" ");
        }
        if (request.historySize() != null) {
            appProperties.getMusicApi().getQueue().setHistorySize(request.historySize());
            logMsg.append("HistorySize=").append(request.historySize()).append(" ");
        }
        if (request.maxUserSongs() != null) {
            appProperties.getMusicApi().getQueue().setMaxUserSongs(request.maxUserSongs());
            logMsg.append("MaxUserSongs=").append(request.maxUserSongs()).append(" ");
        }
        if (request.maxPlaylistImportSize() != null) {
            appProperties.getMusicApi().getPlayer().setMaxPlaylistImportSize(request.maxPlaylistImportSize());
            logMsg.append("MaxPlaylistImportSize=").append(request.maxPlaylistImportSize()).append(" ");
        }
        if (request.maxChatHistorySize() != null) {
            appProperties.getMusicApi().getChat().setMaxHistorySize(request.maxChatHistorySize());
            logMsg.append("MaxChatHistorySize=").append(request.maxChatHistorySize()).append(" ");
        }
        if (request.minChatIntervalMs() != null) {
            appProperties.getMusicApi().getChat().setMinIntervalMs(request.minChatIntervalMs());
            logMsg.append("MinChatInterval=").append(request.minChatIntervalMs()).append("ms ");
        }
        if (request.maxChatMessageLength() != null) {
            appProperties.getMusicApi().getChat().setMaxMessageLength(request.maxChatMessageLength());
            logMsg.append("MaxChatMessageLength=").append(request.maxChatMessageLength()).append(" ");
        }
        if (request.neteaseEnabled() != null) {
            appProperties.getMusicApi().getNetease().setEnabled(request.neteaseEnabled());
            logMsg.append("NeteaseEnabled=").append(request.neteaseEnabled()).append(" ");
        }
        if (request.bilibiliEnabled() != null) {
            appProperties.getMusicApi().getBilibili().setEnabled(request.bilibiliEnabled());
            logMsg.append("BilibiliEnabled=").append(request.bilibiliEnabled()).append(" ");
        }

        ChannelState cs = getOrCreateChannelState(channelId);
        if (request.voteSkipEnabled() != null) {
            cs.isVoteSkipEnabled.set(request.voteSkipEnabled());
            logMsg.append("VoteSkipEnabled=").append(request.voteSkipEnabled()).append(" ");
        }
        if (request.voteSkipThreshold() != null) {
            cs.voteSkipThreshold.set(request.voteSkipThreshold());
            logMsg.append("VoteSkipThreshold=").append(request.voteSkipThreshold()).append(" ");
        }
        if (request.voteSkipWaitTime() != null) {
            cs.voteSkipWaitTime.set(request.voteSkipWaitTime());
            logMsg.append("VoteSkipWaitTime=").append(request.voteSkipWaitTime()).append("s ");
        }

        log.info(logMsg.toString().trim());

        if (cs.isVoteSkipEnabled.get() && cs.currentMusic.get() != null) {
            checkVoteSkipThreshold(channelId);
        }

        broadcastFullPlayerState(channelId);
    }

    public void updateConfig(AdminConfigUpdateRequest request) {
        updateConfig(request, DEFAULT_CHANNEL_ID);
    }

    public void togglePause(String sessionId, Long channelId) {
        ChannelState cs = getOrCreateChannelState(channelId);
        if (cs.currentMusic.get() == null) {
            if (!queueManager.getQueueSnapshot(channelId).isEmpty()) {
                playNextInQueue(channelId);
            }
            return;
        }
        if (isRateLimited(sessionId, channelId)) return;

        if (!"SYSTEM".equals(sessionId)) {
            if (cs.isPauseLocked.get() && !cs.isPaused.get()) {
                return;
            }
        }

        long currentPos = calculateCurrentPosition(cs);
        boolean newState = !cs.isPaused.get();
        cs.isPaused.set(newState);
        cs.positionAnchor.set(currentPos);
        cs.timestampAnchor.set(System.currentTimeMillis());

        log.info("Player {} by {} in channel {}", newState ? "PAUSED" : "RESUMED", getUserName(sessionId), channelId);
        broadcastFullPlayerState(channelId);
        eventPublisher.publishEvent(new SystemMessageEvent(this,
                SystemMessageEvent.Level.INFO,
                newState ? PlayerAction.PAUSE : PlayerAction.PLAY,
                getUserToken(sessionId), null, channelId));
    }

    public void togglePause(String sessionId) {
        togglePause(sessionId, DEFAULT_CHANNEL_ID);
    }

    public void cyclePlayMode(String sessionId, Long channelId) {
        if (isRateLimited(sessionId, channelId)) return;
        ChannelState cs = getOrCreateChannelState(channelId);
        if (cs.isPlayModeLocked.get() && !"SYSTEM".equals(sessionId)) return;

        PlayMode current;
        PlayMode next;
        do {
            current = cs.playMode.get();
            next = switch (current) {
                case SEQUENTIAL -> PlayMode.SHUFFLE;
                case SHUFFLE -> PlayMode.REPEAT_ONE;
                case REPEAT_ONE -> PlayMode.SEQUENTIAL;
            };
        } while (!cs.playMode.compareAndSet(current, next));

        cs.isShuffle.set(next == PlayMode.SHUFFLE);

        log.info("Play mode cycled to {} by {} in channel {}", next, getUserName(sessionId), channelId);
        broadcastFullPlayerState(channelId);

        String modeName = switch (next) {
            case SEQUENTIAL -> "顺序播放";
            case SHUFFLE -> "随机播放";
            case REPEAT_ONE -> "单曲循环";
        };
        eventPublisher.publishEvent(new SystemMessageEvent(this,
                SystemMessageEvent.Level.INFO,
                PlayerAction.MODE_CHANGE, getUserToken(sessionId), modeName, channelId));
    }

    public void cyclePlayMode(String sessionId) {
        cyclePlayMode(sessionId, DEFAULT_CHANNEL_ID);
    }

    public void resetSystem(Long channelId) {
        log.warn("!!!SYSTEM RESET INITIATED for channel {}!!!", channelId);
        ChannelState cs = getOrCreateChannelState(channelId);
        cs.currentMusic.set(null);
        cs.positionAnchor.set(0);
        cs.timestampAnchor.set(0);
        queueManager.clearAll(channelId);
        cs.isPaused.set(false);
        cs.isShuffle.set(false);
        cs.playMode.set(PlayMode.SEQUENTIAL);
        cs.isLoading.set(false);
        broadcastFullPlayerState(channelId);
        broadcastQueueUpdate(channelId);
        log.warn("System reset complete for channel {}.", channelId);
        eventPublisher.publishEvent(new SystemMessageEvent(this,
                SystemMessageEvent.Level.WARN, PlayerAction.RESET, "SYSTEM", null, channelId));
    }

    public void resetSystem() {
        resetSystem(DEFAULT_CHANNEL_ID);
    }

    public void clearQueue(Long channelId) {
        queueManager.clearPendingQueue(channelId);
        log.info("Queue cleared by Admin in channel {}.", channelId);
        broadcastQueueUpdate(channelId);
        eventPublisher.publishEvent(new SystemMessageEvent(this,
                SystemMessageEvent.Level.WARN, PlayerAction.SYSTEM_MESSAGE, "SYSTEM",
                "管理员已清空播放列表", channelId));
    }

    public void clearQueue() {
        clearQueue(DEFAULT_CHANNEL_ID);
    }

    @EventListener
    public void handleDownloadEvent(DownloadStatusEvent event) {
        for (Map.Entry<Long, ChannelState> entry : channelStates.entrySet()) {
            Long channelId = entry.getKey();
            boolean existsInQueue = queueManager.getQueueSnapshot(channelId).stream()
                    .anyMatch(item -> item.music().id().equals(event.getMusicId()));
            if (existsInQueue) {
                log.debug("Download status changed for {}, updating queue UI in channel {}.", event.getMusicId(), channelId);
                broadcastQueueUpdate(channelId);
                ChannelState cs = entry.getValue();
                if (cs.currentMusic.get() == null) {
                    playNextInQueue(channelId);
                }
            }
        }
    }

    @EventListener
    public void onUserCountChanged(UserCountChangeEvent event) {
        Long channelId = event.getChannelId();
        if (event.getOnlineUserCount() == 0 && !isStreamActive.get()) {
            enterIdleMode(channelId);
        }
        ChannelState cs = getOrCreateChannelState(channelId);
        if (cs.isVoteSkipEnabled.get() && cs.currentMusic.get() != null) {
            checkVoteSkipThreshold(channelId);
            broadcastFullPlayerState(channelId);
        }
    }

    @EventListener
    public void onStreamStatusChanged(StreamStatusEvent event) {
        boolean hasListeners = event.isHasListeners();
        this.isStreamActive.set(hasListeners);
        log.info("System: Stream active status changed to: {}, Count: {}", hasListeners, event.getListenerCount());

        for (Map.Entry<Long, ChannelState> entry : channelStates.entrySet()) {
            Long channelId = entry.getKey();
            ChannelState cs = entry.getValue();
            if (hasListeners) {
                if (cs.currentMusic.get() == null) {
                    playNextInQueue(channelId);
                } else if (cs.isPaused.get() && getChannelOnlineUsers(channelId).isEmpty()) {
                    togglePause("SYSTEM", channelId);
                }
            } else {
                if (getChannelOnlineUsers(channelId).isEmpty()) {
                    enterIdleMode(channelId);
                }
            }
            broadcastFullPlayerState(channelId);
        }
    }

    private void enterIdleMode(Long channelId) {
        log.info("Last user disconnected from channel {}. Entering idle mode.", channelId);
        ChannelState cs = getOrCreateChannelState(channelId);
        cs.isLoading.set(false);
        if (cs.currentMusic.get() != null && !cs.isPaused.get()) {
            long currentPos = calculateCurrentPosition(cs);
            if (cs.isPaused.compareAndSet(false, true)) {
                cs.positionAnchor.set(currentPos);
                cs.timestampAnchor.set(System.currentTimeMillis());
                log.info("Player paused in channel {} as all users have disconnected. Position saved at: {}", channelId, currentPos);
                broadcastFullPlayerState(channelId);
            }
        }
    }

    @Scheduled(fixedRate = 600000)
    public void cleanupIdlePlayer() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, ChannelState> entry : channelStates.entrySet()) {
            Long channelId = entry.getKey();
            ChannelState cs = entry.getValue();
            if (cs.isPaused.get() && cs.currentMusic.get() != null) {
                long pausedDuration = now - cs.timestampAnchor.get();
                if (pausedDuration > IDLE_RESET_TIMEOUT_MS) {
                    log.info("Idle player timeout reached in channel {}. Resetting now playing.", channelId);
                    cs.currentMusic.set(null);
                    cs.positionAnchor.set(0);
                    cs.timestampAnchor.set(0);
                    cs.isPaused.set(false);
                    broadcastFullPlayerState(channelId);
                }
            }
        }
    }

    public void broadcastQueueUpdate(Long channelId) {
        eventPublisher.publishEvent(new QueueUpdateEvent(this, channelId, getQueueWithUpdatedStatus(channelId)));
    }

    public void broadcastQueueUpdate() {
        broadcastQueueUpdate(DEFAULT_CHANNEL_ID);
    }

    public void broadcastFullPlayerState(Long channelId) {
        eventPublisher.publishEvent(new PlayerStateEvent(this, channelId, getCurrentPlayerState(channelId)));
    }

    public void broadcastFullPlayerState() {
        broadcastFullPlayerState(DEFAULT_CHANNEL_ID);
    }

    public void broadcastOnlineUsers(Long channelId) {
        broadcastFullPlayerState(channelId);
    }

    public void broadcastOnlineUsers() {
        broadcastOnlineUsers(DEFAULT_CHANNEL_ID);
    }

    private List<MusicQueueItem> getQueueWithUpdatedStatus(Long channelId) {
        return queueManager.getQueueSnapshot(channelId).stream().map(item -> {
            if ("netease".equals(item.music().platform())) {
                return item.status() == QueueItemStatus.READY ? item : item.withStatus(QueueItemStatus.READY);
            }
            if ("bilibili".equals(item.music().platform())) {
                CacheStatus cacheStatus = localCacheService.getStatus(item.music().id());
                QueueItemStatus newStatus = mapCacheStatusToEnum(cacheStatus);
                if (item.status() != newStatus) {
                    return item.withStatus(newStatus);
                }
            }
            return item;
        }).collect(Collectors.toList());
    }

    private Map<String, QueueItemStatus> buildStatusMap(Long channelId) {
        Map<String, QueueItemStatus> statusMap = new HashMap<>();
        for (MusicQueueItem item : queueManager.getQueueSnapshot(channelId)) {
            if ("bilibili".equals(item.music().platform())) {
                statusMap.put(item.music().id(), mapCacheStatusToEnum(localCacheService.getStatus(item.music().id())));
            } else {
                statusMap.put(item.music().id(), QueueItemStatus.READY);
            }
        }
        return statusMap;
    }

    private QueueItemStatus mapCacheStatusToEnum(CacheStatus status) {
        if (status == null) return QueueItemStatus.PENDING;
        return switch (status) {
            case COMPLETED -> QueueItemStatus.READY;
            case DOWNLOADING -> QueueItemStatus.DOWNLOADING;
            case FAILED -> QueueItemStatus.FAILED;
            default -> QueueItemStatus.PENDING;
        };
    }

    private boolean isRateLimited(String userId, Long channelId) {
        long now = System.currentTimeMillis();
        ChannelState cs = getOrCreateChannelState(channelId);
        if (now - cs.lastControlTimestamp.get() < GLOBAL_COOLDOWN_MS) {
            log.warn("Action rate limited for user: {} in channel {}", userId, channelId);
            return true;
        }
        cs.lastControlTimestamp.set(now);
        return false;
    }

    public AppProperties getAppProperties() {
        return appProperties;
    }

    private String getUserToken(String sessionId) {
        if ("SYSTEM".equals(sessionId)) return "SYSTEM";
        return userService.getUser(sessionId).map(User::getToken).orElse("UNKNOWN_TOKEN");
    }

    private String getUserName(String sessionId) {
        return userService.getUser(sessionId).map(User::getName).orElse("Unknown User");
    }

    private Set<String> getChannelOnlineTokens(Long channelId) {
        Set<String> channelSessions = channelSessionManager.getChannelSessions(channelId);
        return userService.getOnlineUserSummaries().stream()
                .filter(u -> channelSessions.contains(u.sessionId()))
                .map(UserSummary::token)
                .collect(Collectors.toSet());
    }

    private List<UserSummary> getChannelOnlineUsers(Long channelId) {
        Set<String> channelSessions = channelSessionManager.getChannelSessions(channelId);
        return userService.getOnlineUserSummaries().stream()
                .filter(u -> channelSessions.contains(u.sessionId()))
                .toList();
    }

    private static class ChannelState {
        final AtomicReference<PlayableMusic> currentMusic = new AtomicReference<>(null);
        final AtomicReference<String> currentEnqueuerId = new AtomicReference<>(null);
        final AtomicReference<String> currentEnqueuerName = new AtomicReference<>(null);
        final AtomicLong positionAnchor = new AtomicLong(0);
        final AtomicLong timestampAnchor = new AtomicLong(0);
        final AtomicBoolean isShuffle = new AtomicBoolean(false);
        final AtomicReference<PlayMode> playMode = new AtomicReference<>(PlayMode.SEQUENTIAL);
        final AtomicBoolean isFairShuffle = new AtomicBoolean(true);
        final AtomicBoolean allowOfflineShuffle = new AtomicBoolean(false);
        final AtomicBoolean isPaused = new AtomicBoolean(false);
        final AtomicBoolean isPauseLocked = new AtomicBoolean(false);
        final AtomicBoolean isSkipLocked = new AtomicBoolean(false);
        final AtomicBoolean isPlayModeLocked = new AtomicBoolean(false);
        final AtomicBoolean isLoading = new AtomicBoolean(false);
        final AtomicLong playHeadVersion = new AtomicLong(0);
        final AtomicBoolean isVoteSkipEnabled = new AtomicBoolean(false);
        final AtomicReference<Double> voteSkipThreshold = new AtomicReference<>(0.5);
        final AtomicReference<Integer> voteSkipWaitTime = new AtomicReference<>(15);
        final Set<String> skipVotes = ConcurrentHashMap.newKeySet();
        final Set<String> likedUserIds = ConcurrentHashMap.newKeySet();
        final List<Long> likeMarkers = new CopyOnWriteArrayList<>();
        final Map<String, Object> likeLock = new HashMap<>();
        final AtomicLong lastControlTimestamp = new AtomicLong(0);
    }
}
