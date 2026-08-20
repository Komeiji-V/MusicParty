package org.thornex.musicparty.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thornex.musicparty.config.SecurityConfig;
import org.thornex.musicparty.entity.CookiePoolItem;
import org.thornex.musicparty.entity.CookieSubmission;
import org.thornex.musicparty.repository.CookiePoolItemRepository;
import org.thornex.musicparty.repository.CookieSubmissionRepository;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.repository.UserTitleRepository;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.entity.UserTitle;
import org.thornex.musicparty.service.CookiePoolService;
import org.thornex.musicparty.service.api.MusicProviderFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 音源 Cookie 池：用户提交 → 总管理员审核 → 汇入池 + 授予「音源提供者」称号。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CookieController {

    public static final String TITLE_COOKIE_PROVIDER = "音源提供者";

    private final CookiePoolService cookiePoolService;
    private final CookieSubmissionRepository submissionRepository;
    private final CookiePoolItemRepository poolRepository;
    private final UserRepository userRepository;
    private final UserTitleRepository userTitleRepository;
    private final MusicProviderFactory musicProviderFactory;
    private final org.thornex.musicparty.util.CryptoUtil crypto;
    private final org.thornex.musicparty.util.IpRateLimiter ipRateLimiter;

    // H1/M3：提交限流（IP+用户双键，5 次/小时）与输入约束
    private static final int SUBMIT_RATE_MAX = 5;
    private static final long SUBMIT_RATE_WINDOW_MS = 3_600_000L;
    private static final int COOKIE_MAX_LENGTH = 4096;
    private static final List<String> ALLOWED_PLATFORMS = List.of("netease", "qq", "kugou", "bilibili");

    /** H1：存量明文 submissions 一次性迁移加密（幂等，enc: 前缀跳过） */
    @jakarta.annotation.PostConstruct
    public void migratePlaintextSubmissions() {
        try {
            int migrated = 0;
            for (CookieSubmission s : submissionRepository.findAll()) {
                String c = s.getCookie();
                if (c != null && !c.isBlank() && !c.startsWith("enc:")) {
                    s.setCookie(crypto.encrypt(c));
                    submissionRepository.save(s);
                    migrated++;
                }
            }
            if (migrated > 0) {
                log.info("迁移了 {} 条存量明文 Cookie 提交为加密存储", migrated);
            }
        } catch (Exception e) {
            log.error("存量 Cookie 提交迁移失败", e);
        }
    }

    // ============ 用户提交 ============

    @PostMapping("/api/cookies/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest request) {
        Long userId = SecurityConfig.getCurrentUserId();
        String platform = body.get("platform");
        String cookie = body.get("cookie");
        if (platform == null || platform.isBlank() || cookie == null || cookie.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "平台与 Cookie 不能为空"));
        }
        // M3：platform 白名单 + 长度上限（防畸形输入/超长凭证刷库）
        if (!ALLOWED_PLATFORMS.contains(platform.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不支持的平台"));
        }
        if (cookie.length() > COOKIE_MAX_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cookie 内容过长"));
        }
        // M3：IP + 用户双键限流（防批量灌垃圾凭证拖垮审核队列）
        String rateKey = "submit:" + request.getRemoteAddr() + ":" + userId;
        if (!ipRateLimiter.allow(rateKey, SUBMIT_RATE_MAX, SUBMIT_RATE_WINDOW_MS)) {
            return ResponseEntity.status(429).body(Map.of("message", "提交过于频繁，请稍后再试"));
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(403).body(Map.of("message", "用户不存在"));
        }
        // 同一平台已有待审核/通过的提交则拒绝重复提交
        if (submissionRepository.existsByUserIdAndPlatformAndStatus(userId, platform, CookieSubmission.Status.PENDING)) {
            return ResponseEntity.badRequest().body(Map.of("message", "该平台已有待审核的提交，请等待审核"));
        }
        CookieSubmission sub = CookieSubmission.builder()
                .userId(userId)
                .username(user.getUsername())
                .platform(platform)
                // H1：审核凭证明文入库 → 加密存储
                .cookie(crypto.encrypt(cookie))
                .status(CookieSubmission.Status.PENDING)
                .build();
        submissionRepository.save(sub);
        log.info("Cookie submission #{} from {} for platform {}", sub.getId(), user.getUsername(), platform);
        return ResponseEntity.ok(Map.of("message", "提交成功，等待总管理员审核", "id", sub.getId()));
    }

    @GetMapping("/api/cookies/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> mySubmissions() {
        Long userId = SecurityConfig.getCurrentUserId();
        List<Map<String, Object>> list = submissionRepository.findByStatusOrderByCreatedAtAsc(CookieSubmission.Status.PENDING).stream()
                .filter(s -> s.getUserId().equals(userId))
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(), "platform", s.getPlatform(),
                        "status", s.getStatus().name(), "createdAt", s.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(list);
    }

    // ============ 总管理员：池管理 ============

    @GetMapping("/api/admin/cookies/pool")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> pool(@RequestParam(required = false) String platform) {
        if (platform != null && !platform.isBlank()) {
            return ResponseEntity.ok(cookiePoolService.list(platform));
        }
        List<Map<String, Object>> result = List.of("netease", "qq", "kugou", "bilibili").stream()
                .map(p -> Map.<String, Object>of(
                        "platform", p,
                        "items", cookiePoolService.list(p).stream()
                                .map(i -> Map.<String, Object>ofEntries(
                                        Map.entry("id", i.getId()),
                                        Map.entry("cookie", mask(i.getCookie())),
                                        Map.entry("enabled", i.isEnabled()),
                                        Map.entry("failCount", i.getFailCount()),
                                        Map.entry("errorMark", i.isErrorMark()),
                                        Map.entry("errorReason", i.getErrorReason() != null ? i.getErrorReason() : ""),
                                        Map.entry("lastErrorAt", i.getLastErrorAt() != null ? i.getLastErrorAt().toString() : ""),
                                        Map.entry("vipType", i.getVipType()),
                                        Map.entry("vipCheckedAt", i.getVipCheckedAt() != null ? i.getVipCheckedAt().toString() : ""),
                                        Map.entry("addedBy", i.getAddedBy() != null ? i.getAddedBy() : -1L),
                                        Map.entry("submittedBy", resolveSubmitter(i.getAddedBy())),
                                        Map.entry("createdAt", i.getCreatedAt() != null ? i.getCreatedAt().toString() : "")))
                                .toList()))
                .toList();
        return ResponseEntity.ok(result);
    }

    /** 提交者用户名（addedBy 为 userId；环境变量种子无提交者返回空串） */
    private String resolveSubmitter(Long userId) {
        if (userId == null) return "";
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("");
    }

    @PostMapping("/api/admin/cookies/pool")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> addToPool(@RequestBody Map<String, String> body) {
        String platform = body.get("platform");
        String cookie = body.get("cookie");
        if (platform == null || platform.isBlank() || cookie == null || cookie.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "平台与 Cookie 不能为空"));
        }
        cookiePoolService.add(platform, cookie, SecurityConfig.getCurrentUserId());
        return ResponseEntity.ok(Map.of("message", "已加入 Cookie 池"));
    }

    @DeleteMapping("/api/admin/cookies/pool/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> removeFromPool(@PathVariable Long id) {
        // 记录提交者（删除后用于判断是否回收「音源提供者」称号）
        Long addedBy = poolRepository.findById(id)
                .map(CookiePoolItem::getAddedBy)
                .orElse(null);
        cookiePoolService.remove(id);
        if (addedBy != null) {
            autoRevokeTitleIfNoCookiesLeft(addedBy);
        }
        return ResponseEntity.ok(Map.of("message", "已移除"));
    }

    /**
     * 自动回收：某用户池内已没有任何 Cookie 时，收回其「音源提供者」称号。
     * 仅当该称号仍在、且该用户不再拥有任何池内 Cookie 时执行。
     */
    private void autoRevokeTitleIfNoCookiesLeft(Long userId) {
        long remaining = poolRepository.countByAddedBy(userId);
        if (remaining > 0) return;
        List<UserTitle> matches = userTitleRepository.findByUserIdOrderByGrantedAtAsc(userId).stream()
                .filter(t -> TITLE_COOKIE_PROVIDER.equals(t.getTitle()))
                .toList();
        if (matches.isEmpty()) return;
        userTitleRepository.deleteAll(matches);
        userRepository.findById(userId).ifPresent(u -> {
            if (TITLE_COOKIE_PROVIDER.equals(u.getCurrentTitle())) {
                u.setCurrentTitle(null);
                userRepository.save(u);
            }
        });
        log.info("Cookie 池无剩余提交：自动回收用户 {} 的「{}」称号", userId, TITLE_COOKIE_PROVIDER);
    }

    @PutMapping("/api/admin/cookies/pool/{id}/enabled")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> setPoolEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean enabled = body.get("enabled") instanceof Boolean b && b;
        cookiePoolService.setEnabled(id, enabled);
        return ResponseEntity.ok(Map.of("message", enabled ? "已启用" : "已禁用"));
    }

    /** 管理员手动清除错误标记（并重新启用） */
    @PostMapping("/api/admin/cookies/pool/{id}/clear-error")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> clearError(@PathVariable Long id) {
        cookiePoolService.clearError(id);
        return ResponseEntity.ok(Map.of("message", "错误标记已清除，Cookie 已重新启用"));
    }

    /** 检测 Cookie 的 VIP 状态（目前仅网易云支持） */
    @PostMapping("/api/admin/cookies/pool/{id}/check-vip")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> checkVip(@PathVariable Long id) {
        CookiePoolItem item = poolRepository.findById(id).orElse(null);
        if (item == null) {
            return ResponseEntity.status(404).body(Map.of("message", "条目不存在"));
        }
        if (!"netease".equals(item.getPlatform())) {
            return ResponseEntity.badRequest().body(Map.of("message", "目前仅支持检测网易云 Cookie 的 VIP 状态"));
        }
        var provider = musicProviderFactory.getProvider("netease");
        if (!(provider instanceof org.thornex.musicparty.service.api.NeteaseMusicProvider netease)) {
            return ResponseEntity.badRequest().body(Map.of("message", "网易云音源不可用"));
        }
        Integer vipType = netease.checkVip(item.getCookie()).block(java.time.Duration.ofSeconds(15));
        cookiePoolService.saveVipResult(id, vipType != null ? vipType : -1);
        String desc;
        if (vipType == null || vipType == -1) {
            desc = "Cookie 无效或未登录，无法判定 VIP";
        } else if (vipType > 0) {
            desc = "VIP 会员（等级 " + vipType + "）";
        } else {
            desc = "普通用户（非 VIP）";
        }
        return ResponseEntity.ok(Map.of("message", desc, "vipType", vipType != null ? vipType : -1));
    }

    // ============ 总管理员：审核 ============

    @GetMapping("/api/admin/cookies/submissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> submissions(@RequestParam(required = false) String status) {
        CookieSubmission.Status st = status != null ? CookieSubmission.Status.valueOf(status) : CookieSubmission.Status.PENDING;
        List<Map<String, Object>> list = submissionRepository.findByStatusOrderByCreatedAtAsc(st).stream()
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(), "username", s.getUsername(), "platform", s.getPlatform(),
                        // H1：存量明文兼容 + 新数据密文 → 统一解密后掩码展示
                        "cookie", org.thornex.musicparty.util.CryptoUtil.mask(crypto.decrypt(s.getCookie())),
                        "createdAt", s.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/api/admin/cookies/submissions/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        CookieSubmission sub = submissionRepository.findById(id).orElse(null);
        if (sub == null || sub.getStatus() != CookieSubmission.Status.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("message", "提交不存在或已处理"));
        }
        // 1. 汇入 Cookie 池
        cookiePoolService.add(sub.getPlatform(), crypto.decrypt(sub.getCookie()), sub.getUserId());
        // 2. 授予「音源提供者」称号
        grantTitle(sub.getUserId(), TITLE_COOKIE_PROVIDER, "Cookie 审核通过（" + sub.getPlatform() + "）");
        // 3. 更新状态；H1：审核完成后清除明文凭证（仅保留审计行：谁/何时/哪个平台）
        sub.setCookie(null);
        sub.setStatus(CookieSubmission.Status.APPROVED);
        sub.setReviewedBy(SecurityConfig.getCurrentUserId());
        sub.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(sub);
        log.info("Cookie submission #{} approved, {} granted", id, TITLE_COOKIE_PROVIDER);
        return ResponseEntity.ok(Map.of("message", "已通过：Cookie 已汇入池，用户获得「" + TITLE_COOKIE_PROVIDER + "」称号"));
    }

    @PostMapping("/api/admin/cookies/submissions/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        CookieSubmission sub = submissionRepository.findById(id).orElse(null);
        if (sub == null || sub.getStatus() != CookieSubmission.Status.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("message", "提交不存在或已处理"));
        }
        sub.setStatus(CookieSubmission.Status.REJECTED);
        // H1：驳回也清除凭证（驳回的 Cookie 往往仍有效，不能明文留存）
        sub.setCookie(null);
        sub.setReviewedBy(SecurityConfig.getCurrentUserId());
        sub.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(sub);
        return ResponseEntity.ok(Map.of("message", "已拒绝"));
    }

    private void grantTitle(Long userId, String title, String source) {
        if (!userTitleRepository.existsByUserIdAndTitle(userId, title)) {
            org.thornex.musicparty.entity.UserTitle ut = org.thornex.musicparty.entity.UserTitle.builder()
                    .userId(userId).title(title).source(source).build();
            userTitleRepository.save(ut);
            // 首次获得称号时自动设为当前称号
            userRepository.findById(userId).ifPresent(u -> {
                if (u.getCurrentTitle() == null || u.getCurrentTitle().isBlank()) {
                    u.setCurrentTitle(title);
                    userRepository.save(u);
                }
            });
        }
    }

    private String mask(String cookie) {
        if (cookie == null || cookie.length() <= 8) return "******";
        return cookie.substring(0, 4) + "****" + cookie.substring(cookie.length() - 4);
    }
}
