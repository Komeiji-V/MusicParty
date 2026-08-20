package org.thornex.musicparty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.thornex.musicparty.config.SecurityConfig;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.entity.TitleDef;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.repository.TitleDefRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 个人主页公开展示：内容池 + 自动排版模板。
 * 用户维护一个内容池（歌曲/专辑/歌词部件，可排序、写备注），
 * 再选择整体版式模板（hero 主视觉 / magazine 杂志双栏 / minimal 极简 / wall 封面墙），
 * 展示区按模板自动排版，无需手动拖拽。
 * 部件结构：{id, kind: song|album|lyric, data: {...}, note}（顺序即展示顺序）。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    /** 合法版式模板；未知/缺省回退 hero */
    private static final java.util.Set<String> VALID_TEMPLATES =
            java.util.Set.of("hero", "magazine", "minimal", "wall");

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final TitleDefRepository titleDefRepository;

    /** 编辑自己的主页展示（内容池 + 版式模板） */
    @PutMapping("/api/profile/featured")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateFeatured(@RequestBody Map<String, Object> body) {
        Long userId = SecurityConfig.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(403).body(Map.of("message", "用户不存在"));
        try {
            if (body.containsKey("template")) {
                String template = body.get("template") != null ? String.valueOf(body.get("template")) : "";
                user.setFeaturedTemplate(VALID_TEMPLATES.contains(template) ? template : "hero");
            }
            if (body.containsKey("widgets")) {
                // 非法内容不保存（保留原值），由调用方自行修正
                List<Map<String, Object>> widgets = validateWidgets(body.get("widgets"));
                if (widgets != null) {
                    user.setFeaturedWidgets(objectMapper.writeValueAsString(widgets));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "数据格式错误：" + e.getMessage()));
        }
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "主页展示已更新"));
    }

    /** 公开查看某用户的主页展示 */
    @GetMapping("/api/public/users/{username}/featured")
    public ResponseEntity<?> featured(@PathVariable String username) {
        // 忽略大小写匹配，容忍 Preview_Admin / preview_admin 等写法
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        String title = null;
        String color = null;
        if (user.getCurrentTitle() != null && !user.getCurrentTitle().isBlank()) {
            title = user.getCurrentTitle();
            color = titleDefRepository.findByName(title)
                    .map(TitleDef::getColor)
                    .orElse("#ff5722");
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("username", user.getUsername());
        result.put("title", title != null ? title : "");
        result.put("titleColor", color != null ? color : "");
        String template = user.getFeaturedTemplate();
        result.put("template", template != null && VALID_TEMPLATES.contains(template) ? template : "hero");
        result.put("widgets", resolveWidgets(user));
        return ResponseEntity.ok(result);
    }

    /**
     * 解析并校验内容池；非法返回 null（调用方不保存）。
     * 每项 {id, kind: song|album|lyric, data: {...}, note}，id 唯一。
     * 兼容旧数据中的 x/y/w/h 字段（忽略）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> validateWidgets(Object raw) {
        if (!(raw instanceof List)) return null;
        List<Map<String, Object>> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Object o : (List<Object>) raw) {
            if (!(o instanceof Map)) return null;
            Map<String, Object> m = (Map<String, Object>) o;
            String id = m.get("id") == null ? "" : String.valueOf(m.get("id"));
            if (id.isBlank() || !seen.add(id)) return null;
            String kind = m.get("kind") == null ? "" : String.valueOf(m.get("kind"));
            if (!List.of("song", "album", "lyric").contains(kind)) return null;
            if (!(m.get("data") instanceof Map)) return null;
            String size = m.get("size") == null ? "" : String.valueOf(m.get("size"));
            if (!size.isEmpty() && !List.of("1x1", "2x2", "1x2").contains(size)) return null;
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", id);
            item.put("kind", kind);
            item.put("data", m.get("data"));
            item.put("size", size.isEmpty() ? "1x1" : size);
            item.put("note", m.get("note") != null ? String.valueOf(m.get("note")) : "");
            out.add(item);
        }
        return out;
    }

    /** 读取内容池；旧数据（旧 featuredSong/Album/Lyric 字段）时迁移 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveWidgets(User user) {
        Object stored = readJson(user.getFeaturedWidgets());
        if (stored instanceof List) {
            List<Map<String, Object>> widgets = validateWidgets(stored);
            if (widgets != null && !widgets.isEmpty()) return widgets;
        }
        // 旧数据迁移：featuredSong / featuredAlbum / favoriteLyric → 内容池
        List<Map<String, Object>> def = new ArrayList<>();
        int seq = 0;
        Object song = readJson(user.getFeaturedSong());
        if (song instanceof Map) {
            def.add(widget("song-" + (++seq), "song", song, ""));
        }
        Object album = readJson(user.getFeaturedAlbum());
        if (album instanceof Map) {
            def.add(widget("album-" + (++seq), "album", album, "我最喜欢的专辑"));
        }
        String lyric = user.getFavoriteLyric();
        if (lyric != null && !lyric.isBlank()) {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("song", user.getFavoriteLyricSong() != null ? user.getFavoriteLyricSong() : "");
            data.put("text", lyric);
            def.add(widget("lyric-" + (++seq), "lyric", data, ""));
        }
        return def;
    }

    private Map<String, Object> widget(String id, String kind, Object data, String note) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", id);
        m.put("kind", kind);
        m.put("data", data);
        m.put("note", note);
        return m;
    }

    /** 解析存储的 JSON；空/非法时返回 null（前端显示空态） */
    private Object readJson(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            return objectMapper.readValue(stored, Object.class);
        } catch (Exception e) {
            return stored; // 兼容旧文本数据
        }
    }
}
