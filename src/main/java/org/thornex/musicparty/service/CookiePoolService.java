package org.thornex.musicparty.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.entity.CookiePoolItem;
import org.thornex.musicparty.entity.SystemConfig;
import org.thornex.musicparty.repository.CookiePoolItemRepository;
import org.thornex.musicparty.repository.SystemConfigRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 音源 Cookie 池：每个平台维护多个 Cookie，请求时轮换使用，
 * 某个 Cookie 连续失败自动禁用并切换到下一个。
 * 支持手动指定"当前使用"的 Cookie（优先使用，失败仍会切换）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CookiePoolService {

    /** 连续失败多少次后自动禁用该 Cookie */
    private static final int FAIL_THRESHOLD = 3;

    private static final List<String> ALL_PLATFORMS = List.of("netease", "qq", "kugou", "bilibili");

    private final CookiePoolItemRepository repository;
    private final AppProperties appProperties;
    private final SystemConfigRepository systemConfigRepository;
    private final org.thornex.musicparty.util.CryptoUtil crypto;

    private final Map<String, CopyOnWriteArrayList<CookiePoolItem>> pool = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobin = new ConcurrentHashMap<>();
    /** 手动选中的 Cookie id（key = channelKey(channelId, platform) → itemId）；null 表示自动轮询 */
    private final Map<String, Long> selectedIds = new ConcurrentHashMap<>();

    /** 频道级选择的 key；channelId 为 null 表示全局/默认（搜索等无频道上下文时使用） */
    private static String channelKey(Long channelId, String platform) {
        return (channelId == null ? "default" : channelId) + ":" + platform;
    }

    /** 持久化 key：cookie_selected_{channelId或default}_{platform} */
    private static String persistKey(Long channelId, String platform) {
        return "cookie_selected_" + (channelId == null ? "default" : channelId) + "_" + platform;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** 从数据库重新加载池（管理端增删后调用） */
    public synchronized void reload() {
        pool.clear();
        roundRobin.clear();
        selectedIds.clear();
        // 恢复各频道手动选中的 Cookie（存于 system_config，key: cookie_selected_{channelId}_{platform}）
        for (SystemConfig cfg : systemConfigRepository.findAll()) {
            String key = cfg.getConfigKey();
            if (key == null || !key.startsWith("cookie_selected_")) continue;
            String value = cfg.getConfigValue();
            if (value == null || value.isBlank()) continue;
            try {
                // key 形如 cookie_selected_{cid}_{platform}
                String rest = key.substring("cookie_selected_".length());
                int idx = rest.lastIndexOf('_');
                if (idx <= 0) continue;
                String cid = rest.substring(0, idx);
                String platform = rest.substring(idx + 1);
                Long id = Long.parseLong(value);
                String mapKey = "default".equals(cid) ? channelKey(null, platform) : channelKey(Long.parseLong(cid), platform);
                selectedIds.put(mapKey, id);
            } catch (NumberFormatException ignored) {
            }
        }
        // 环境变量中的 Cookie 作为初始池条目（保证开箱即用）
        seedFromEnv("netease", appProperties.getMusicApi().getNetease().getCookie());
        seedFromEnv("qq", appProperties.getMusicApi().getQq().getCookie());
        seedFromEnv("kugou", appProperties.getMusicApi().getKugou().getCookie());
        seedFromEnv("bilibili", appProperties.getMusicApi().getBilibili().getSessdata());

        // 一次性迁移：存量明文 cookie 加密落库（幂等，enc: 前缀跳过）
        for (CookiePoolItem item : repository.findAll()) {
            if (item.getCookie() != null && !item.getCookie().startsWith("enc:")) {
                item.setCookie(crypto.encrypt(item.getCookie()));
                repository.save(item);
                log.info("Cookie pool [{}]: migrated plaintext cookie #{} to encrypted storage", item.getPlatform(), item.getId());
            }
        }

        for (CookiePoolItem item : repository.findAll()) {
            if (item.isEnabled()) {
                // 池内存对象用解密后的明文副本（DB 实体保持密文）
                pool.computeIfAbsent(item.getPlatform(), k -> new CopyOnWriteArrayList<>()).add(toMemoryItem(item));
            }
        }
        pool.forEach((platform, list) ->
                log.info("Cookie pool [{}]: {} enabled cookie(s), selected={}", platform, list.size(), selectedIds.get(platform)));
    }

    /** DB 实体（cookie 为密文）→ 内存明文副本（非 JPA 托管，避免脏写回库） */
    private CookiePoolItem toMemoryItem(CookiePoolItem db) {
        return CookiePoolItem.builder()
                .id(db.getId())
                .platform(db.getPlatform())
                .cookie(crypto.decrypt(db.getCookie()))
                .enabled(db.isEnabled())
                .failCount(db.getFailCount())
                .errorMark(db.isErrorMark())
                .errorReason(db.getErrorReason())
                .lastErrorAt(db.getLastErrorAt())
                .vipType(db.getVipType())
                .vipCheckedAt(db.getVipCheckedAt())
                .addedBy(db.getAddedBy())
                .createdAt(db.getCreatedAt())
                .build();
    }

    /** 把内存明文对象同步回 DB（cookie 加密存储） */
    private void persistPoolItem(CookiePoolItem mem) {
        repository.findById(mem.getId()).ifPresent(db -> {
            db.setCookie(crypto.encrypt(mem.getCookie()));
            db.setEnabled(mem.isEnabled());
            db.setFailCount(mem.getFailCount());
            db.setErrorMark(mem.isErrorMark());
            db.setErrorReason(mem.getErrorReason());
            db.setLastErrorAt(mem.getLastErrorAt());
            db.setVipType(mem.getVipType());
            db.setVipCheckedAt(mem.getVipCheckedAt());
            repository.save(db);
        });
    }

    private void seedFromEnv(String platform, String cookie) {
        if (cookie == null || cookie.isBlank() || "YOUR_NETEASE_COOKIE_STRING_HERE".equals(cookie)) return;
        // 兼容存量明文记录：加密值与明文值都查一遍
        boolean exists = !repository.findByPlatformAndCookie(platform, crypto.encrypt(cookie)).isEmpty()
                || !repository.findByPlatformAndCookie(platform, cookie).isEmpty();
        if (!exists) {
            CookiePoolItem item = CookiePoolItem.builder()
                    .platform(platform).cookie(crypto.encrypt(cookie)).enabled(true).addedBy(null).build();
            repository.save(item);
            log.info("Seeded {} cookie from environment into pool (encrypted)", platform);
        }
    }

    /** 轮换取下一个可用 Cookie（全局轮询；空池返回 null） */
    public String next(String platform) {
        CopyOnWriteArrayList<CookiePoolItem> list = pool.get(platform);
        if (list == null || list.isEmpty()) return null;
        int idx = roundRobin.computeIfAbsent(platform, k -> new AtomicInteger()).getAndIncrement();
        for (int i = 0; i < list.size(); i++) {
            CookiePoolItem item = list.get((idx + i) % list.size());
            if (item.isEnabled()) {
                return item.getCookie();
            }
        }
        return null;
    }

    /** 返回某频道手动选中的 Cookie 值（未选中/已禁用/已移除返回 null） */
    public String getSelectedCookie(Long channelId, String platform) {
        Long id = selectedIds.get(channelKey(channelId, platform));
        if (id == null) return null;
        CopyOnWriteArrayList<CookiePoolItem> list = pool.get(platform);
        if (list == null) return null;
        return list.stream()
                .filter(i -> i.getId().equals(id) && i.isEnabled())
                .map(CookiePoolItem::getCookie)
                .findFirst()
                .orElse(null);
    }

    /** 某频道当前选中的 Cookie id（无选中或选中项不可用返回 null） */
    public Long getSelectedId(Long channelId, String platform) {
        Long id = selectedIds.get(channelKey(channelId, platform));
        if (id == null) return null;
        // 选中项被禁用/移除后视为未选中
        CookiePoolItem item = repository.findById(id).orElse(null);
        if (item == null || !item.isEnabled()) return null;
        return id;
    }

    /** 频道管理员手动指定该频道当前使用的 Cookie（仅限启用中的；禁用的需先启用） */
    @Transactional
    public boolean select(Long channelId, String platform, Long id) {
        CookiePoolItem item = repository.findById(id).orElse(null);
        if (item == null || !platform.equals(item.getPlatform()) || !item.isEnabled()) {
            return false;
        }
        selectedIds.put(channelKey(channelId, platform), id);
        persistSelection(channelId, platform, String.valueOf(id));
        log.info("Cookie pool [{}]: channel {} manually selected cookie #{}", platform, channelId, id);
        return true;
    }

    /** 取消某频道的手动选择，恢复自动轮询 */
    @Transactional
    public void unselect(Long channelId, String platform) {
        selectedIds.remove(channelKey(channelId, platform));
        persistSelection(channelId, platform, "");
        log.info("Cookie pool [{}]: channel {} manual selection cleared", platform, channelId);
    }

    private void persistSelection(Long channelId, String platform, String value) {
        String key = persistKey(channelId, platform);
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseGet(() -> SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        systemConfigRepository.save(config);
    }

    /** 记录一次失败；自动打上错误标记并累加失败次数，达到阈值自动禁用该 Cookie */
    public void markFailure(String platform, String cookie, String reason) {
        if (cookie == null || cookie.isBlank()) return;
        CopyOnWriteArrayList<CookiePoolItem> list = pool.get(platform);
        if (list == null) return;
        list.stream().filter(i -> cookie.equals(i.getCookie())).findFirst().ifPresent(item -> {
            int fails = item.getFailCount() + 1;
            item.setFailCount(fails);
            item.setErrorMark(true);
            item.setErrorReason(reason != null ? reason : "调用失败");
            item.setLastErrorAt(java.time.LocalDateTime.now());
            persistPoolItem(item);
            if (fails >= FAIL_THRESHOLD) {
                log.warn("Cookie pool [{}]: cookie disabled after {} consecutive failures ({})", platform, fails, reason);
            } else {
                log.warn("Cookie pool [{}]: cookie failure {} / {} ({})", platform, fails, FAIL_THRESHOLD, reason);
            }
        });
    }

    public void markFailure(String platform, String cookie) {
        markFailure(platform, cookie, "调用失败");
    }

    /** 记录一次成功（清除错误标记与失败计数） */
    public void markSuccess(String platform, String cookie) {
        if (cookie == null || cookie.isBlank()) return;
        CopyOnWriteArrayList<CookiePoolItem> list = pool.get(platform);
        if (list == null) return;
        list.stream().filter(i -> cookie.equals(i.getCookie()))
                .filter(i -> i.getFailCount() > 0 || i.isErrorMark())
                .findFirst().ifPresent(item -> {
                    item.setFailCount(0);
                    item.setErrorMark(false);
                    item.setErrorReason(null);
                    item.setLastErrorAt(null);
                    persistPoolItem(item);
                });
    }

    /** 管理员手动清除错误标记 */
    @Transactional
    public void clearError(Long id) {
        repository.findById(id).ifPresent(item -> {
            item.setErrorMark(false);
            item.setErrorReason(null);
            item.setLastErrorAt(null);
            item.setFailCount(0);
            if (!item.isEnabled()) {
                item.setEnabled(true);
            }
            repository.save(item);
        });
        reload(); // 池内重新加载（含解密），保证启用状态一致
    }

    /** 保存 VIP 检测结果 */
    @Transactional
    public void saveVipResult(Long id, int vipType) {
        repository.findById(id).ifPresent(item -> {
            item.setVipType(vipType);
            item.setVipCheckedAt(java.time.LocalDateTime.now());
            repository.save(item);
        });
    }

    /** 返回某平台的全部 Cookie（含禁用的，管理用；cookie 为解密明文） */
    public List<CookiePoolItem> list(String platform) {
        return repository.findByPlatformOrderByIdAsc(platform).stream()
                .map(this::toMemoryItem)
                .toList();
    }

    /** 返回某平台当前启用的全部 Cookie（用于请求内逐个重试；频道选中优先级由调用方按 channelId 处理） */
    public List<String> allEnabled(String platform) {
        CopyOnWriteArrayList<CookiePoolItem> list = pool.get(platform);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().filter(CookiePoolItem::isEnabled).map(CookiePoolItem::getCookie).toList();
    }

    @Transactional
    public CookiePoolItem add(String platform, String cookie, Long addedBy) {
        CookiePoolItem item = CookiePoolItem.builder()
                .platform(platform).cookie(crypto.encrypt(cookie)).enabled(true).failCount(0).addedBy(addedBy).build();
        item = repository.save(item);
        CookiePoolItem mem = toMemoryItem(item);
        pool.computeIfAbsent(platform, k -> new CopyOnWriteArrayList<>()).add(mem);
        return mem;
    }

    @Transactional
    public void remove(Long id) {
        repository.findById(id).ifPresent(item -> {
            repository.delete(item);
            CopyOnWriteArrayList<CookiePoolItem> list = pool.get(item.getPlatform());
            if (list != null) list.remove(item);
        });
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        repository.findById(id).ifPresent(item -> {
            item.setEnabled(enabled);
            if (enabled) item.setFailCount(0);
            repository.save(item);
        });
        reload(); // 池内重新加载（含解密）
    }
}
