package org.thornex.musicparty.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.entity.MusicCache;
import org.thornex.musicparty.entity.SystemConfig;
import org.thornex.musicparty.repository.*;

import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemConfigService {

    private static final String CACHE_NAME = "systemConfig";

    private final SystemConfigRepository systemConfigRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PlayHistoryRepository playHistoryRepository;
    private final MusicQueueItemRepository musicQueueItemRepository;
    private final MusicCacheRepository musicCacheRepository;
    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final EntityManager entityManager;
    private final AppProperties appProperties;
    private final ChannelSessionManager channelSessionManager;

    @Cacheable(value = CACHE_NAME, key = "'siteTitle'")
    public String getSiteTitle() {
        return systemConfigRepository.findByConfigKey("site_title")
                .map(SystemConfig::getConfigValue)
                .orElse("MUSIC PARTY");
    }

    @Cacheable(value = CACHE_NAME, key = "'authorName'")
    public String getAuthorName() {
        return systemConfigRepository.findByConfigKey("author_name")
                .map(SystemConfig::getConfigValue)
                .orElse(appProperties.getMusicApi().getAuthorName());
    }

    @Cacheable(value = CACHE_NAME, key = "'backWords'")
    public String getBackWords() {
        return systemConfigRepository.findByConfigKey("back_words")
                .map(SystemConfig::getConfigValue)
                .orElse(appProperties.getMusicApi().getBackWords());
    }

    @Cacheable(value = CACHE_NAME, key = "'infoPageContent'")
    public String getInfoPageContent() {
        return systemConfigRepository.findByConfigKey("info_page_content")
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    /** 首页 ABOUT 介绍文字（可编辑）；为空时前端显示内置默认文案 */
    @Cacheable(value = CACHE_NAME, key = "'aboutText'")
    public String getAboutText() {
        return systemConfigRepository.findByConfigKey("about_text")
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    public boolean hasInfoPage() {
        String content = getInfoPageContent();
        return content != null && !content.isEmpty();
    }

    @CacheEvict(value = CACHE_NAME, key = "'siteTitle'")
    @Transactional
    public void setSiteTitle(String title) {
        saveOrUpdateConfig("site_title", title);
    }

    @CacheEvict(value = CACHE_NAME, key = "'authorName'")
    @Transactional
    public void setAuthorName(String name) {
        saveOrUpdateConfig("author_name", name);
    }

    @CacheEvict(value = CACHE_NAME, key = "'backWords'")
    @Transactional
    public void setBackWords(String words) {
        saveOrUpdateConfig("back_words", words);
    }

    @CacheEvict(value = CACHE_NAME, key = "'infoPageContent'")
    @Transactional
    public void setInfoPageContent(String html) {
        saveOrUpdateConfig("info_page_content", html);
    }

    @CacheEvict(value = CACHE_NAME, key = "'aboutText'")
    @Transactional
    public void setAboutText(String text) {
        saveOrUpdateConfig("about_text", text);
    }

    @Transactional
    public void saveOrUpdateConfig(String key, String value) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseGet(() -> SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        systemConfigRepository.save(config);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        long channelCount = channelRepository.count();
        long userCount = userRepository.count();
        long queueCount = musicQueueItemRepository.count();
        stats.put("channels", channelCount);
        stats.put("users", userCount);
        stats.put("chatMessages", chatMessageRepository.count());
        stats.put("playHistory", playHistoryRepository.count());
        stats.put("queueItems", queueCount);

        List<MusicCache> caches = musicCacheRepository.findAll();
        stats.put("cacheFiles", caches.size());
        long totalSize = caches.stream().mapToLong(c -> c.getFileSize() != null ? c.getFileSize() : 0).sum();
        stats.put("cacheTotalSize", totalSize);

        stats.put("totalUsers", userCount);
        stats.put("totalChannels", channelCount);
        stats.put("totalSongs", queueCount);
        int onlineUsers = channelSessionManager.getActiveChannels().stream()
                .mapToInt(channelId -> channelSessionManager.getChannelSessions(channelId).size())
                .sum();
        stats.put("onlineUsers", onlineUsers);

        return stats;
    }

    @Transactional
    public int cleanupChatMessages(Integer olderThanDays, Long channelId) {
        LocalDateTime before = LocalDateTime.now().minusDays(olderThanDays != null ? olderThanDays : 30);
        if (channelId != null) {
            var messages = chatMessageRepository.findByChannelIdAndCreatedAtBefore(channelId, before);
            chatMessageRepository.deleteAll(messages);
            int count = messages.size();
            log.info("清理了频道 {} 的 {} 条聊天记录", channelId, count);
            return count;
        } else {
            var allMessages = chatMessageRepository.findAll();
            var toDelete = allMessages.stream()
                    .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isBefore(before))
                    .toList();
            chatMessageRepository.deleteAll(toDelete);
            int count = toDelete.size();
            log.info("清理了所有频道的 {} 条聊天记录", count);
            return count;
        }
    }

    @Transactional
    public int cleanupPlayHistory(Integer olderThanDays, Long channelId) {
        LocalDateTime before = LocalDateTime.now().minusDays(olderThanDays != null ? olderThanDays : 30);
        if (channelId != null) {
            var histories = playHistoryRepository.findByChannelId(channelId);
            var toDelete = histories.stream()
                    .filter(h -> h.getPlayedAt() != null && h.getPlayedAt().isBefore(before))
                    .toList();
            playHistoryRepository.deleteAll(toDelete);
            int count = toDelete.size();
            log.info("清理了频道 {} 的 {} 条播放历史", channelId, count);
            return count;
        } else {
            var allHistories = playHistoryRepository.findAll();
            var toDelete = allHistories.stream()
                    .filter(h -> h.getPlayedAt() != null && h.getPlayedAt().isBefore(before))
                    .toList();
            playHistoryRepository.deleteAll(toDelete);
            int count = toDelete.size();
            log.info("清理了所有频道的 {} 条播放历史", count);
            return count;
        }
    }

    @Transactional
    public int cleanupQueueItems(Long channelId) {
        if (channelId != null) {
            var failedItems = musicQueueItemRepository.findByChannelIdAndStatus(channelId, "FAILED");
            musicQueueItemRepository.deleteAll(failedItems);
            int count = failedItems.size();
            log.info("清理了频道 {} 的 {} 条队列项", channelId, count);
            return count;
        } else {
            var allItems = musicQueueItemRepository.findAll();
            var toDelete = allItems.stream()
                    .filter(i -> "FAILED".equals(i.getStatus()))
                    .toList();
            musicQueueItemRepository.deleteAll(toDelete);
            int count = toDelete.size();
            log.info("清理了所有频道的 {} 条队列项", count);
            return count;
        }
    }

    @Transactional
    public int cleanupCache(Integer olderThanDays, Long channelId) {
        LocalDateTime before = LocalDateTime.now().minusDays(olderThanDays != null ? olderThanDays : 30);

        List<MusicCache> toDelete;
        if (channelId != null) {
            toDelete = musicCacheRepository.findAll().stream()
                    .filter(c -> channelId.equals(c.getChannelId()) && c.getCachedAt() != null && c.getCachedAt().isBefore(before))
                    .toList();
        } else {
            toDelete = musicCacheRepository.findAll().stream()
                    .filter(c -> c.getCachedAt() != null && c.getCachedAt().isBefore(before))
                    .toList();
        }

        for (MusicCache cache : toDelete) {
            try {
                Path path = Paths.get(cache.getFilePath());
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("删除缓存文件失败: {}", cache.getFilePath(), e);
            }
        }

        musicCacheRepository.deleteAll(toDelete);
        int count = toDelete.size();
        log.info("清理了 {} 个缓存文件", count);
        return count;
    }

    @Transactional
    public Map<String, Object> cleanupAll(Integer olderThanDays) {
        int days = olderThanDays != null ? olderThanDays : 30;
        int chat = cleanupChatMessages(days, null);
        int history = cleanupPlayHistory(days, null);
        int queue = cleanupQueueItems(null);
        int cache = cleanupCache(days, null);

        Map<String, Object> result = new HashMap<>();
        result.put("chatMessages", chat);
        result.put("playHistory", history);
        result.put("queueItems", queue);
        result.put("cacheFiles", cache);
        result.put("total", chat + history + queue + cache);

        log.info("统一清理完成: 聊天{}条, 历史{}条, 队列{}条, 缓存{}个", chat, history, queue, cache);
        return result;
    }

    private static final String KEY_CLEANUP_ENABLED = "cleanup_enabled";
    private static final String KEY_CLEANUP_INTERVAL_HOURS = "cleanup_interval_hours";
    private static final String KEY_CLEANUP_OLDER_THAN_DAYS = "cleanup_older_than_days";
    private static final String KEY_CLEANUP_TARGETS = "cleanup_targets";
    private static final String DEFAULT_CLEANUP_TARGETS = "chat,history,queue,cache";

    public Map<String, Object> getCleanupConfig() {
        boolean enabled = Boolean.parseBoolean(getConfigValue(KEY_CLEANUP_ENABLED, "false"));
        int intervalHours = parseInt(getConfigValue(KEY_CLEANUP_INTERVAL_HOURS, "24"), 24);
        int olderThanDays = parseInt(getConfigValue(KEY_CLEANUP_OLDER_THAN_DAYS, "30"), 30);
        String targetsRaw = getConfigValue(KEY_CLEANUP_TARGETS, DEFAULT_CLEANUP_TARGETS);

        List<String> targets = new java.util.ArrayList<>();
        if (targetsRaw != null && !targetsRaw.isEmpty()) {
            for (String t : targetsRaw.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty() && isSupportedCleanupTarget(trimmed)) {
                    targets.add(trimmed);
                }
            }
        }

        Map<String, Object> config = new HashMap<>();
        config.put("enabled", enabled);
        config.put("intervalHours", intervalHours);
        config.put("olderThanDays", olderThanDays);
        config.put("targets", targets);
        return config;
    }

    @Transactional
    public void setCleanupConfig(Map<String, Object> body) {
        if (body.containsKey("enabled")) {
            saveOrUpdateConfig(KEY_CLEANUP_ENABLED, String.valueOf(Boolean.TRUE.equals(body.get("enabled"))));
        }
        if (body.containsKey("intervalHours")) {
            int value = ((Number) body.get("intervalHours")).intValue();
            saveOrUpdateConfig(KEY_CLEANUP_INTERVAL_HOURS, String.valueOf(Math.max(1, value)));
        }
        if (body.containsKey("olderThanDays")) {
            int value = ((Number) body.get("olderThanDays")).intValue();
            saveOrUpdateConfig(KEY_CLEANUP_OLDER_THAN_DAYS, String.valueOf(Math.max(1, value)));
        }
        if (body.containsKey("targets")) {
            List<String> targets = new java.util.ArrayList<>();
            for (Object t : (Iterable<?>) body.get("targets")) {
                String name = String.valueOf(t).trim().toLowerCase();
                if (isSupportedCleanupTarget(name) && !targets.contains(name)) {
                    targets.add(name);
                }
            }
            saveOrUpdateConfig(KEY_CLEANUP_TARGETS, String.join(",", targets));
        }
        log.info("定时清理配置已更新: {}", body);
    }

    private String getConfigValue(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean isSupportedCleanupTarget(String name) {
        return "chat".equals(name) || "history".equals(name) || "queue".equals(name) || "cache".equals(name);
    }
}
