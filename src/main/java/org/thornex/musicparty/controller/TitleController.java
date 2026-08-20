package org.thornex.musicparty.controller;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thornex.musicparty.config.SecurityConfig;
import org.thornex.musicparty.entity.TitleDef;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.entity.UserTitle;
import org.thornex.musicparty.repository.TitleDefRepository;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.repository.UserTitleRepository;
import org.thornex.musicparty.service.MusicPlayerService;
import org.thornex.musicparty.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 称号系统：总管理员先创建称号定义（名称+颜色），再授予用户。
 * 用户可在个人空间从已拥有的称号中选择一个展示（彩色矩形标签）。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TitleController {

    private final UserTitleRepository titleRepository;
    private final UserRepository userRepository;
    private final TitleDefRepository titleDefRepository;
    private final UserService userService;
    private final MusicPlayerService musicPlayerService;

    @PostConstruct
    public void init() {
        // 系统称号「音源提供者」：Cookie 审核通过自动授予；确保定义存在
        if (titleDefRepository.findByName(CookieController.TITLE_COOKIE_PROVIDER).isEmpty()) {
            titleDefRepository.save(TitleDef.builder()
                    .name(CookieController.TITLE_COOKIE_PROVIDER)
                    .color("#ff5722")
                    .build());
            log.info("Seeded default title definition: {}", CookieController.TITLE_COOKIE_PROVIDER);
        }
    }

    /** 查询称号定义的颜色（未定义时返回默认色） */
    public String resolveColor(String title) {
        if (title == null || title.isBlank()) return "";
        return titleDefRepository.findByName(title).map(TitleDef::getColor).orElse("#ff5722");
    }

    // ============ 用户端 ============

    /** 我的称号列表（含颜色）与当前选用 */
    @GetMapping("/api/titles/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> mine() {
        Long userId = SecurityConfig.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        String current = user != null && user.getCurrentTitle() != null ? user.getCurrentTitle() : "";
        List<Map<String, Object>> titles = titleRepository.findByUserIdOrderByGrantedAtAsc(userId).stream()
                .map(t -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("title", t.getTitle());
                    item.put("color", resolveColor(t.getTitle()));
                    item.put("current", t.getTitle().equals(current));
                    return item;
                })
                .toList();
        return ResponseEntity.ok(Map.of(
                "titles", titles,
                "current", current,
                "currentColor", resolveColor(current)
        ));
    }

    /** 选用当前称号（必须已拥有；传空则取消称号） */
    @PutMapping("/api/titles/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> choose(@RequestBody Map<String, String> body) {
        Long userId = SecurityConfig.getCurrentUserId();
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            userRepository.findById(userId).ifPresent(u -> {
                u.setCurrentTitle(null);
                userRepository.save(u);
            });
            notifyTitleChanged(userId);
            return ResponseEntity.ok(Map.of("message", "已取消称号"));
        }
        if (!titleRepository.existsByUserIdAndTitle(userId, title)) {
            return ResponseEntity.status(403).body(Map.of("message", "未拥有该称号"));
        }
        userRepository.findById(userId).ifPresent(u -> {
            u.setCurrentTitle(title);
            userRepository.save(u);
        });
        notifyTitleChanged(userId);
        return ResponseEntity.ok(Map.of("message", "称号已切换为「" + title + "」"));
    }

    /** 称号变更后，向该用户所在的所有频道广播全量状态，让在线成员列表实时刷新（无需手动刷新页面） */
    private void notifyTitleChanged(Long userId) {
        userService.getOnlineChannelsOfUser(userId).forEach(channelId -> {
            log.info("Title changed, broadcasting online users refresh for channel {}", channelId);
            musicPlayerService.broadcastFullPlayerState(channelId);
        });
    }

    // ============ 总管理员：称号定义管理 ============

    /** 全部称号定义 */
    @GetMapping("/api/admin/titles/defs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> defs() {
        return ResponseEntity.ok(titleDefRepository.findAllByOrderByCreatedAtAsc());
    }

    /** 创建称号定义（先制作，后下发） */
    @PostMapping("/api/admin/titles/defs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> createDef(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String color = body.get("color");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "称号名称不能为空"));
        }
        name = name.trim();
        if (titleDefRepository.findByName(name).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "该称号定义已存在"));
        }
        String finalColor = (color == null || color.isBlank()) ? "#ff5722" : color.trim();
        TitleDef def = titleDefRepository.save(TitleDef.builder().name(name).color(finalColor).build());
        log.info("Title definition created: {} ({})", name, finalColor);
        return ResponseEntity.ok(Map.of("message", "称号「" + name + "」已创建", "id", def.getId()));
    }

    /** 删除称号定义（已授予的称号保留，颜色回退默认） */
    @DeleteMapping("/api/admin/titles/defs/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteDef(@PathVariable Long id) {
        TitleDef def = titleDefRepository.findById(id).orElse(null);
        if (def == null) {
            return ResponseEntity.status(404).body(Map.of("message", "定义不存在"));
        }
        titleDefRepository.delete(def);
        return ResponseEntity.ok(Map.of("message", "已删除称号定义「" + def.getName() + "」"));
    }

    /**
     * 修改称号定义颜色（名称不可改：用户持有的称号按名称关联，改名会导致失联）。
     * 改色立即同步到所有已授予该称号的用户展示。
     */
    @PutMapping("/api/admin/titles/defs/{id}/color")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> updateDefColor(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String color = body.get("color");
        if (color == null || color.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "颜色不能为空"));
        }
        TitleDef def = titleDefRepository.findById(id).orElse(null);
        if (def == null) {
            return ResponseEntity.status(404).body(Map.of("message", "定义不存在"));
        }
        def.setColor(color.trim());
        titleDefRepository.save(def);
        log.info("Title definition [{}] color updated to {}", def.getName(), color);
        return ResponseEntity.ok(Map.of("message", "颜色已更新，持有者展示即时生效"));
    }

    // ============ 总管理员：称号授予管理 ============

    /** 全部已授予的称号（管理用） */
    @GetMapping("/api/admin/titles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> allTitles() {
        List<Map<String, Object>> list = titleRepository.findAll().stream()
                .map(t -> {
                    String username = userRepository.findById(t.getUserId())
                            .map(User::getUsername).orElse("?");
                    return Map.<String, Object>of(
                            "id", t.getId(), "userId", t.getUserId(), "username", username,
                            "title", t.getTitle(),
                            "color", resolveColor(t.getTitle()),
                            "source", t.getSource() != null ? t.getSource() : "",
                            "grantedAt", t.getGrantedAt() != null ? t.getGrantedAt().toString() : "");
                })
                .toList();
        return ResponseEntity.ok(list);
    }

    /** 授予用户称号（必须是已创建的称号定义） */
    @PostMapping("/api/admin/titles/grant")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> grant(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String title = body.get("title");
        if (username == null || username.isBlank() || title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名与称号不能为空"));
        }
        if (titleDefRepository.findByName(title).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "称号「" + title + "」尚未创建，请先创建称号定义"));
        }
        User target = userRepository.findByUsername(username).orElse(null);
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
        }
        if (titleRepository.existsByUserIdAndTitle(target.getId(), title)) {
            return ResponseEntity.badRequest().body(Map.of("message", "该用户已拥有此称号"));
        }
        UserTitle ut = UserTitle.builder()
                .userId(target.getId()).title(title).source("总管理员自定义").build();
        titleRepository.save(ut);
        if (target.getCurrentTitle() == null || target.getCurrentTitle().isBlank()) {
            target.setCurrentTitle(title);
            userRepository.save(target);
        }
        log.info("Title [{}] granted to {} by admin", title, username);
        return ResponseEntity.ok(Map.of("message", "已授予「" + title + "」给 " + username));
    }

    /** 收回称号 */
    @PostMapping("/api/admin/titles/revoke")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> revoke(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String title = body.get("title");
        if (username == null || username.isBlank() || title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名与称号不能为空"));
        }
        User target = userRepository.findByUsername(username).orElse(null);
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
        }
        List<UserTitle> matches = titleRepository.findByUserIdOrderByGrantedAtAsc(target.getId()).stream()
                .filter(t -> title.equals(t.getTitle()))
                .toList();
        if (matches.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "该用户没有此称号"));
        }
        titleRepository.deleteAll(matches);
        if (title.equals(target.getCurrentTitle())) {
            target.setCurrentTitle(null);
            userRepository.save(target);
        }
        log.info("Title [{}] revoked from {} by admin", title, username);
        return ResponseEntity.ok(Map.of("message", "已收回「" + title + "」"));
    }
}
