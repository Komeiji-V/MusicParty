package org.thornex.musicparty.controller;

import org.springframework.web.bind.annotation.*;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.dto.Music;
import org.thornex.musicparty.dto.Playlist;
import org.thornex.musicparty.dto.UserSearchResult;
import org.thornex.musicparty.exception.ApiRequestException;
import org.thornex.musicparty.service.SystemConfigService;
import org.thornex.musicparty.service.api.MusicProvider;
import org.thornex.musicparty.service.api.MusicProviderFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final MusicProviderFactory providerFactory;
    private final AppProperties appProperties;
    private final SystemConfigService systemConfigService;

    public ApiController(MusicProviderFactory providerFactory, AppProperties appProperties,
                         SystemConfigService systemConfigService) {
        this.providerFactory = providerFactory;
        this.appProperties = appProperties;
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return Map.of(
                "siteTitle", systemConfigService.getSiteTitle(),
                "authorName", systemConfigService.getAuthorName(),
                "backWords", systemConfigService.getBackWords(),
                "aboutText", systemConfigService.getAboutText(),
                "hasInfoPage", systemConfigService.hasInfoPage(),
                "authCenterUrl", appProperties.getAuthCenter().getUrl()
        );
    }

    @GetMapping("/config/info")
    public Map<String, String> getInfoPage() {
        return Map.of("content", systemConfigService.getInfoPageContent());
    }

    private MusicProvider getService(String platform) {
        if (!providerFactory.isProviderEnabled(platform)) {
            throw new ApiRequestException(platform + " 音源已被禁用");
        }
        MusicProvider provider = providerFactory.getProvider(platform);
        if (provider == null) {
            throw new ApiRequestException("不支持的音乐平台: " + platform);
        }
        return provider;
    }

    @GetMapping("/search/{platform}/{keyword}")
    public Mono<List<Music>> searchMusic(@PathVariable String platform, @PathVariable String keyword) {
        return getService(platform).searchMusic(keyword, 30, 0);
    }

    @GetMapping("/user/playlists/{platform}/{userId}")
    public Mono<List<Playlist>> getUserPlaylists(@PathVariable String platform, @PathVariable String userId) {
        return getService(platform).getUserPlaylists(userId);
    }

    @GetMapping("/playlist/songs/{platform}/{playlistId}")
    public Mono<List<Music>> getPlaylistSongs(@PathVariable String platform,
                                              @PathVariable String playlistId,
                                              @RequestParam(defaultValue = "0") int offset,
                                              @RequestParam(defaultValue = "20") int limit) {
        return getService(platform).getPlaylistSongs(playlistId, offset, limit);
    }

    @GetMapping("/user/search/{platform}/{keyword}")
    public Mono<List<UserSearchResult>> searchUsers(@PathVariable String platform, @PathVariable String keyword) {
        return getService(platform).searchUsers(keyword);
    }

    @GetMapping("/music/lyric/{platform}/{musicId}")
    public Mono<String> getLyric(@PathVariable String platform, @PathVariable String musicId) {
        return getService(platform).getLyric(musicId);
    }
}
