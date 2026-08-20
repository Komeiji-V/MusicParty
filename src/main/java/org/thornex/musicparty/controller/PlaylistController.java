package org.thornex.musicparty.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.thornex.musicparty.config.SecurityConfig;
import org.thornex.musicparty.dto.*;
import org.thornex.musicparty.service.UserPlaylistService;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/user/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final UserPlaylistService playlistService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(playlistService.list(SecurityConfig.getCurrentUserId()));
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> categories() {
        return ResponseEntity.ok(playlistService.categories(SecurityConfig.getCurrentUserId()));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> create(@RequestBody CreatePlaylistRequest req) {
        return ResponseEntity.ok(playlistService.create(SecurityConfig.getCurrentUserId(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdatePlaylistRequest req) {
        return ResponseEntity.ok(playlistService.update(SecurityConfig.getCurrentUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        playlistService.delete(SecurityConfig.getCurrentUserId(), id);
        return ResponseEntity.ok(Map.of("message", "歌单已删除"));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getItems(@PathVariable Long id) {
        return ResponseEntity.ok(playlistService.getItems(SecurityConfig.getCurrentUserId(), id));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addItem(@PathVariable Long id, @RequestBody AddPlaylistItemRequest req) {
        if (req.music() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "歌曲数据不能为空"));
        }
        return ResponseEntity.ok(playlistService.addItem(SecurityConfig.getCurrentUserId(), id, req.music()));
    }

    @PostMapping("/{id}/import")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> importSongs(@PathVariable Long id, @RequestBody ImportPlaylistItemsRequest req) {
        int added = playlistService.importSongs(SecurityConfig.getCurrentUserId(), id, req.songs());
        return ResponseEntity.ok(Map.of("added", added));
    }

    /** 使用图片 URL 作为歌单封面 */
    @PutMapping("/{id}/cover-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> setCoverUrl(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String url = playlistService.updateCoverUrl(SecurityConfig.getCurrentUserId(), id, body.get("url"));
        return ResponseEntity.ok(Map.of("message", "封面已更新", "coverUrl", url));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        playlistService.removeItem(SecurityConfig.getCurrentUserId(), id, itemId);
        return ResponseEntity.ok(Map.of("message", "歌曲已移除"));
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam(defaultValue = "txt") String format) {
        Long userId = SecurityConfig.getCurrentUserId();
        String name = playlistService.getPlaylistName(userId, id);
        String ext;
        MediaType mediaType;
        byte[] body;

        if ("json".equalsIgnoreCase(format)) {
            ext = "json";
            mediaType = MediaType.APPLICATION_JSON;
            try {
                body = objectMapper.writeValueAsBytes(playlistService.exportJson(userId, id));
            } catch (JsonProcessingException e) {
                body = "{\"error\":\"export failed\"}".getBytes(StandardCharsets.UTF_8);
            }
        } else {
            ext = "txt";
            mediaType = MediaType.TEXT_PLAIN;
            body = playlistService.exportTxt(userId, id).getBytes(StandardCharsets.UTF_8);
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(name + "." + ext, StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .body(body);
    }
}
