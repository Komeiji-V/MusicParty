package org.thornex.musicparty.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@RequiredArgsConstructor
public class CleanupScheduler {

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long ONE_HOUR_MS = 3_600_000L;

    private final SystemConfigService systemConfigService;
    private final org.thornex.musicparty.repository.CookieSubmissionRepository cookieSubmissionRepository;

    private final AtomicLong lastRunAt = new AtomicLong(0L);

    @Scheduled(fixedRate = ONE_MINUTE_MS)
    public void scheduledCleanup() {
        Map<String, Object> config = systemConfigService.getCleanupConfig();
        if (!Boolean.TRUE.equals(config.get("enabled"))) {
            return;
        }

        int intervalHours = ((Number) config.getOrDefault("intervalHours", 24)).intValue();
        long now = System.currentTimeMillis();
        long last = lastRunAt.get();
        if (last != 0 && now - last < (long) Math.max(1, intervalHours) * ONE_HOUR_MS) {
            return;
        }

        List<String> targets = (List<String>) config.getOrDefault("targets", List.of());
        if (targets.isEmpty()) {
            return;
        }

        int olderThanDays = ((Number) config.getOrDefault("olderThanDays", 30)).intValue();
        executeCleanup(targets, olderThanDays);
        lastRunAt.set(now);
    }

    private synchronized void executeCleanup(List<String> targets, int olderThanDays) {
        Map<String, Object> result = new java.util.HashMap<>();
        for (String target : targets) {
            try {
                switch (target) {
                    case "chat" -> result.put("chat", systemConfigService.cleanupChatMessages(olderThanDays, null));
                    case "history" -> result.put("history", systemConfigService.cleanupPlayHistory(olderThanDays, null));
                    case "queue" -> result.put("queue", systemConfigService.cleanupQueueItems(null));
                    case "cache" -> result.put("cache", systemConfigService.cleanupCache(olderThanDays, null));
                    case "rejected_cookies" -> result.put("rejected_cookies",
                            cleanupRejectedCookieSubmissions(olderThanDays));
                    default -> log.warn("跳过未知的定时清理目标: {}", target);
                }
            } catch (Exception e) {
                log.error("定时清理目标 {} 执行失败", target, e);
            }
        }
        log.info("定时清理完成: {}", result);
    }

    /** H1：清理超期的已驳回 Cookie 提交（驳回凭证不留存） */
    private int cleanupRejectedCookieSubmissions(int olderThanDays) {
        try {
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(Math.max(1, olderThanDays));
            List<org.thornex.musicparty.entity.CookieSubmission> expired =
                    cookieSubmissionRepository.findByStatusOrderByCreatedAtAsc(org.thornex.musicparty.entity.CookieSubmission.Status.REJECTED)
                            .stream()
                            .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isBefore(cutoff))
                            .toList();
            cookieSubmissionRepository.deleteAll(expired);
            log.info("清理了 {} 条超期已驳回的 Cookie 提交", expired.size());
            return expired.size();
        } catch (Exception e) {
            log.error("清理已驳回 Cookie 提交失败", e);
            return -1;
        }
    }
}
