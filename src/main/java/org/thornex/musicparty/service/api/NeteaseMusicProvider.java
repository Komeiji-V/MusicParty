package org.thornex.musicparty.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.dto.*;
import org.thornex.musicparty.exception.ApiRequestException;
import org.thornex.musicparty.service.CookiePoolService;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Component
@Slf4j
@RequiredArgsConstructor
public class NeteaseMusicProvider implements MusicProvider {

    private final WebClient webClient;
    private final AppProperties appProperties;
    private final CookiePoolService cookiePoolService;
    private static final String PLATFORM = "netease";

    private String getBaseUrl() {
        return appProperties.getMusicApi().getNetease().getBaseUrl();
    }

    private String getDefaultQuality() {
        return appProperties.getMusicApi().getNetease().getQuality();
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing NeteaseCloudMusic provider with quality: {} (cookie pool mode)...", getDefaultQuality());
    }

    private void ensureConfigured() {
        // Cookie 池模式下无需预配置；池为空时 VIP/高音质功能受限
    }

    private String upgradeToHttps(String url) {
        if (url != null && url.startsWith("http://")) {
            return url.replace("http://", "https://");
        }
        return url;
    }

    private Mono<ApiRequestException> handleApiError(String apiName, org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .flatMap(errorBody -> Mono.error(new ApiRequestException(
                        String.format("Netease API '%s' failed with status %d: %s", apiName, response.statusCode().value(), errorBody)
                )));
    }

    private List<String> parseArtists(JsonNode song) {
        List<String> artists = new ArrayList<>();
        JsonNode artistNode = song.has("artists") ? song.path("artists") : song.path("ar");
        if (artistNode.isArray()) {
            artistNode.forEach(artist -> artists.add(artist.path("name").asText()));
        }
        return artists;
    }

    private Music parseSongNode(JsonNode song) {
        return new Music(
                song.path("id").asText(),
                song.path("name").asText(),
                parseArtists(song),
                song.path("dt").asLong(),
                PLATFORM,
                upgradeToHttps(song.path("al").path("picUrl").asText()),
                song.path("al").path("name").asText(null),
                song.has("fee") ? song.path("fee").asInt(0) : null
        );
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public Mono<List<Music>> searchMusic(String keyword, int limit, int offset) {
        return webClient.get()
                .uri(getBaseUrl() + "/cloudsearch?keywords={keyword}&limit={limit}&offset={offset}",
                        keyword, limit, offset)
                // M3：Cookie 走请求头，避免进入 URL（ncm-api 代理访问日志）
                .header("Cookie", getCookie())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleApiError("search", response))
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    List<Music> musicList = new ArrayList<>();
                    JsonNode songs = jsonNode.path("result").path("songs");
                    if (songs.isArray()) {
                        songs.forEach(song -> musicList.add(parseSongNode(song)));
                    }
                    return musicList;
                });
    }

    @Override
    public Mono<Music> getSongDetail(String musicId) {
        return webClient.get()
                .uri(getBaseUrl() + "/song/detail?ids={musicId}", musicId)
                .header("Cookie", getCookie())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleApiError("get song detail", response))
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    JsonNode songs = jsonNode.path("songs");
                    if (songs.isArray() && songs.size() > 0) {
                        return parseSongNode(songs.get(0));
                    }
                    throw new ApiRequestException("未找到歌�? " + musicId);
                });
    }

    /** 按专辑名搜索专辑并返回其全部歌曲 + 专辑 ID（供公开主页展示与跳转官方页） */
    public Mono<Map<String, Object>> getAlbumSongs(String albumName) {
        if (albumName == null || albumName.isBlank()) return Mono.just(emptyAlbum());
        return webClient.get()
                .uri(getBaseUrl() + "/cloudsearch?keywords={keyword}&type=10&limit=5",
                        albumName)
                .header("Cookie", getCookie())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleApiError("search album", response))
                .bodyToMono(JsonNode.class)
                .flatMap(json -> {
                    JsonNode albums = json.path("result").path("albums");
                    if (!albums.isArray() || albums.isEmpty()) return Mono.just(emptyAlbum());
                    JsonNode first = albums.get(0);
                    String albumId = first.path("id").asText("");
                    String albumNameResolved = first.path("name").asText(albumName);
                    if (albumId.isEmpty()) return Mono.just(emptyAlbum());
                    log.info("Album [{}] resolved to id={}", albumNameResolved, albumId);
                    return webClient.get()
                            .uri(getBaseUrl() + "/album?id={id}", albumId)
                            .header("Cookie", getCookie())
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, response -> handleApiError("album songs", response))
                            .bodyToMono(JsonNode.class)
                            .map(albumJson -> {
                                List<Music> list = new ArrayList<>();
                                JsonNode songs = albumJson.path("songs");
                                if (songs.isArray()) {
                                    songs.forEach(song -> list.add(parseSongNode(song)));
                                }
                                Map<String, Object> result = new java.util.HashMap<>();
                                result.put("id", albumId);
                                result.put("name", albumNameResolved);
                                result.put("songs", list);
                                return result;
                            });
                })
                .onErrorReturn(emptyAlbum());
    }

    private Map<String, Object> emptyAlbum() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", "");
        result.put("name", "");
        result.put("songs", List.of());
        return result;
    }

    @Override
    public Mono<String> getPlayUrl(String musicId, String quality) {
        String level = quality != null ? quality : getDefaultQuality();
        // Cookie 池：按序逐个尝试，失败自动切换下一个
        List<String> all = cookiePoolService.allEnabled(PLATFORM);
        final List<String> cookies = all.isEmpty() ? List.of("") : all;
        return Mono.defer(() -> {
            Mono<String> chain = Mono.error(new ApiRequestException("no cookie"));
            for (String cookie : cookies) {
                chain = chain.onErrorResume(e -> requestPlayUrl(musicId, level, cookie));
            }
            return chain;
        }).doOnSuccess(url -> {
            if (url != null && !url.isEmpty()) {
                String used = currentUsedCookie.get();
                if (used != null) cookiePoolService.markSuccess(PLATFORM, used);
            }
        });
    }

    /** 按频道取播放地址：该频道手动选中的 Cookie 优先使用，失败再依次尝试其他启用 Cookie */
    @Override
    public Mono<String> getPlayUrl(String musicId, String quality, Long channelId) {
        String level = quality != null ? quality : getDefaultQuality();
        List<String> cookies = new ArrayList<>();
        String selected = channelId != null ? cookiePoolService.getSelectedCookie(channelId, PLATFORM) : null;
        if (selected != null && !selected.isBlank()) {
            cookies.add(selected);
        }
        for (String c : cookiePoolService.allEnabled(PLATFORM)) {
            if (!cookies.contains(c)) cookies.add(c);
        }
        if (cookies.isEmpty()) cookies.add("");
        final List<String> chainCookies = cookies;
        return Mono.defer(() -> {
            Mono<String> chain = Mono.error(new ApiRequestException("no cookie"));
            for (String cookie : chainCookies) {
                chain = chain.onErrorResume(e -> requestPlayUrl(musicId, level, cookie));
            }
            return chain;
        }).doOnSuccess(url -> {
            if (url != null && !url.isEmpty()) {
                String used = currentUsedCookie.get();
                if (used != null) cookiePoolService.markSuccess(PLATFORM, used);
            }
        });
    }

    private final java.util.concurrent.atomic.AtomicReference<String> currentUsedCookie = new java.util.concurrent.atomic.AtomicReference<>();

    private Mono<String> requestPlayUrl(String musicId, String level, String cookie) {
        currentUsedCookie.set(cookie);
        log.info("getPlayUrl: musicId={} level={} cookie=[{}...]", musicId, level,
                maskCookie(cookie));
        return webClient.get()
                .uri(getBaseUrl() + "/song/url/v1?id={musicId}&level={level}", musicId, level)
                .header("Cookie", cookie)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    cookiePoolService.markFailure(PLATFORM, cookie, "播放请求失败 HTTP " + response.statusCode().value());
                    return handleApiError("get song URL", response);
                })
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    JsonNode data = jsonNode.path("data");
                    if (data.isArray() && data.size() > 0) {
                        String url = data.get(0).path("url").asText();
                        boolean freeTrial = data.get(0).hasNonNull("freeTrialInfo");
                        log.info("getPlayUrl result: musicId={} cookie=[{}...] url={} freeTrial={}", musicId,
                                org.thornex.musicparty.util.CryptoUtil.mask(cookie),
                                url == null || url.isEmpty() ? "EMPTY" : "OK(" + url.length() + "chars)", freeTrial);
                        if (url == null || url.isEmpty()) {
                            // 空 URL 通常表示该 Cookie 无权播放（VIP 限制）
                            cookiePoolService.markFailure(PLATFORM, cookie, "播放地址为空（Cookie 无 VIP 权限）");
                            throw new ApiRequestException("播放地址为空（Cookie 无权限），尝试下一个");
                        }
                        if (freeTrial) {
                            // 只拿到 30 秒试听：该 Cookie 无 VIP 权限 → 标记失败并换下一个 Cookie
                            cookiePoolService.markFailure(PLATFORM, cookie, "仅返回试听（Cookie 无 VIP 权限）");
                            throw new ApiRequestException("仅返回试听（Cookie 无 VIP 权限），尝试下一个");
                        }
                        return url;
                    }
                    throw new ApiRequestException("播放地址解析失败");
                });
    }

    /**
     * 检测 Cookie 的 VIP 状态（网易云 /user/account）
     * @return -1 检测失败/未登录无法判定，0 非 VIP，>0 会员等级
     */
    public Mono<Integer> checkVip(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            return Mono.just(-1);
        }
        return webClient.get()
                .uri(getBaseUrl() + "/user/account")
                .header("Cookie", cookie)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleApiError("user account", response))
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    JsonNode account = json.path("account");
                    if (account == null || account.isNull()) {
                        return -1; // Cookie 无效或未登录，无法判定
                    }
                    return account.path("vipType").asInt(0);
                })
                .onErrorReturn(-1);
    }

    @Override
    public Mono<Music> getPlayableMusic(String musicId) {
        return getSongDetail(musicId);
    }

    @Override
    public Mono<List<Playlist>> getUserPlaylists(String userId) {
        return webClient.get()
                .uri(getBaseUrl() + "/user/playlist?uid={userId}", userId)
                .header("Cookie", getCookie())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleApiError("get user playlists", response))
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    List<Playlist> playlists = new ArrayList<>();
                    jsonNode.path("playlist").forEach(pl -> playlists.add(new Playlist(
                            pl.path("id").asText(),
                            pl.path("name").asText(),
                            upgradeToHttps(pl.path("coverImgUrl").asText()),
                            pl.path("trackCount").asInt(),
                            PLATFORM
                    )));
                    return playlists;
                });
    }

    @Override
    public Mono<List<Music>> getPlaylistSongs(String playlistId, int offset, int limit) {
        return webClient.get()
                .uri(getBaseUrl() + "/playlist/track/all?id={playlistId}&limit={limit}&offset={offset}",
                        playlistId, limit, offset)
                .header("Cookie", getCookie())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleApiError("get playlist tracks", response))
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    List<Music> musicList = new ArrayList<>();
                    jsonNode.path("songs").forEach(song -> {
                        Music music = parseSongNode(song);
                        musicList.add(music);
                    });
                    return musicList;
                });
    }

    @Override
    public Mono<List<UserSearchResult>> searchUsers(String keyword) {
        return webClient.get()
                .uri(getBaseUrl() + "/search?keywords={keyword}&type=1002", keyword)
                .header("Cookie", getCookie())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> handleApiError("user search", response))
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    List<UserSearchResult> users = new ArrayList<>();
                    JsonNode profiles = jsonNode.path("result").path("userprofiles");
                    if (profiles.isArray()) {
                        profiles.forEach(u -> users.add(new UserSearchResult(
                                u.path("userId").asText(),
                                u.path("nickname").asText(),
                                upgradeToHttps(u.path("avatarUrl").asText()),
                                PLATFORM
                        )));
                    }
                    return users;
                });
    }

    @Override
    public Mono<String> getLyric(String musicId) {
        return webClient.get()
                .uri(getBaseUrl() + "/lyric?id={id}", musicId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    if (json.has("lrc") && json.get("lrc").has("lyric")) {
                        return json.get("lrc").get("lyric").asText();
                    }
                    return "";
                })
                .onErrorReturn("");
    }

    @Override
    public boolean isEnabled() {
        return appProperties.getMusicApi().getNetease().isEnabled();
    }

    @Override
    public void setCookie(String cookie) {
        // Cookie 池模式：调用即加入池
        if (StringUtils.hasText(cookie)) {
            cookiePoolService.add(PLATFORM, cookie, null);
            log.info("Netease cookie added to pool (via setCookie).");
        }
    }

    @Override
    public String getCookie() {
        // 池模式：每次请求轮换取下一个
        return cookiePoolService.next(PLATFORM);
    }

    /** M3：日志只打掩码（前 4 + *** + 后 4） */
    static String maskCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) return "";
        if (cookie.length() <= 12) return "****";
        return cookie.substring(0, 4) + "***" + cookie.substring(cookie.length() - 4);
    }
}
