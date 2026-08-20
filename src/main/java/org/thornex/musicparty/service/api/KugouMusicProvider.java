package org.thornex.musicparty.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.dto.Music;
import org.thornex.musicparty.dto.Playlist;
import org.thornex.musicparty.dto.UserSearchResult;
import org.thornex.musicparty.exception.ApiRequestException;
import org.thornex.musicparty.service.CookiePoolService;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

@Component
@Slf4j
public class KugouMusicProvider implements MusicProvider {

    private final WebClient webClient;
    private final AppProperties appProperties;
    private final CookiePoolService cookiePoolService;
    private static final String PLATFORM = "kugou";
    private static final String SEARCH_URL = "http://mobilecdn.kugou.com/api/v3/search/song";
    private static final String PLAY_URL = "https://wwwapi.kugou.com/play/songinfo";
    private static final String LRC_URL = "https://lyrics.kugou.com/search";
    private static final String LRC_DOWNLOAD_URL = "https://lyrics.kugou.com/download";

    private static final byte[] XOR_KEY = {64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, (byte) 206, (byte) 210, 110, 105};
    private static final byte[] DECRYPT_KEY = "kugou^_^2023music".getBytes(StandardCharsets.UTF_8);

    public KugouMusicProvider(WebClient webClient, AppProperties appProperties, CookiePoolService cookiePoolService) {
        this.webClient = webClient;
        this.appProperties = appProperties;
        this.cookiePoolService = cookiePoolService;
    }

    @PostConstruct
    public void initialize() {
        if (org.springframework.util.StringUtils.hasText(appProperties.getMusicApi().getKugou().getCookie())) {
            log.info("Kugou env cookie will be seeded into pool");
        }
        log.info("KugouMusic provider initialized.");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void ensureConfigured() {
        if (!appProperties.getMusicApi().getKugou().isEnabled()) {
            throw new ApiRequestException("酷狗音乐源已被禁用");
        }
    }

    private Music parseSongFromNode(JsonNode song) {
        String hash = song.path("hash").asText();
        String name = song.path("songname").asText();
        if (name.isEmpty()) {
            name = song.path("songName").asText();
        }

        List<String> artists = new ArrayList<>();
        String singerName = song.path("singername").asText();
        if (singerName.isEmpty()) {
            singerName = song.path("singerName").asText();
        }
        if (!singerName.isEmpty()) {
            artists.add(singerName);
        }

        long duration = song.path("duration").asLong() * 1000L;
        String albumId = song.path("album_id").asText();
        if (albumId.isEmpty()) {
            albumId = song.path("albumId").asText();
        }

        String coverUrl = "";
        if (!albumId.isEmpty()) {
            coverUrl = "https://imge.kugou.com/stdmusic/{size}/" + albumId + "/" + hash + "/" + hash + ".jpg";
        }

        return new Music(hash, name, artists, duration, PLATFORM, coverUrl);
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public Mono<List<Music>> searchMusic(String keyword, int limit, int offset) {
        ensureConfigured();
        int page = (offset / Math.max(limit, 1)) + 1;

        return webClient.get()
                .uri(SEARCH_URL + "?keyword={keyword}&page={page}&pagesize={limit}",
                        encode(keyword), page, limit)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.kugou.com/")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    List<Music> musicList = new ArrayList<>();
                    JsonNode data = jsonNode.path("data").path("info");
                    if (data.isArray()) {
                        data.forEach(song -> musicList.add(parseSongFromNode(song)));
                    }
                    return musicList;
                })
                .onErrorReturn(Collections.emptyList());
    }

    /** 按专辑名搜索专辑并返回其全部歌曲（含专辑 ID，供跳转官方页） */
    public Mono<Map<String, Object>> getAlbumSongs(String albumName) {
        ensureConfigured();
        return webClient.get()
                .uri("http://mobilecdn.kugou.com/api/v3/search/album?keyword={keyword}&page=1&pagesize=5",
                        encode(albumName))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.kugou.com/")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(jsonNode -> {
                    JsonNode info = jsonNode.path("data").path("info");
                    if (!info.isArray() || info.isEmpty()) return Mono.just(emptyAlbum());
                    JsonNode first = info.get(0);
                    String albumId = first.path("albumid").asText("");
                    String albumNameResolved = first.path("albumname").asText(albumName);
                    if (albumId.isEmpty()) return Mono.just(emptyAlbum());
                    log.info("Kugou Album [{}] resolved to id={}", albumNameResolved, albumId);

                    return webClient.get()
                            .uri("http://mobilecdn.kugou.com/api/v3/album/song?albumid={id}&page=1&pagesize=200",
                                    albumId)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .header("Referer", "https://www.kugou.com/")
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(songJson -> {
                                List<Music> songs = new ArrayList<>();
                                JsonNode list = songJson.path("data").path("info");
                                if (list.isArray()) {
                                    list.forEach(song -> songs.add(parseSongFromNode(song)));
                                }
                                Map<String, Object> result = new HashMap<>();
                                result.put("id", albumId);
                                result.put("name", albumNameResolved);
                                result.put("songs", songs);
                                return result;
                            });
                })
                .onErrorReturn(emptyAlbum());
    }

    private Map<String, Object> emptyAlbum() {
        Map<String, Object> result = new HashMap<>();
        result.put("id", "");
        result.put("name", "");
        result.put("songs", Collections.emptyList());
        return result;
    }

    @Override
    public Mono<Music> getSongDetail(String musicId) {
        ensureConfigured();
        String hash = musicId;
        String albumAudioId = "0";
        if (musicId.contains("|")) {
            String[] parts = musicId.split("\\|");
            hash = parts[0];
            if (parts.length > 1) {
                albumAudioId = parts[1];
            }
        }
        return webClient.get()
                .uri(PLAY_URL + "?hash={hash}&album_audio_id={albumAudioId}", hash, albumAudioId)
                .header("Referer", "https://www.kugou.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    JsonNode data = jsonNode.path("data");
                    String name = data.path("song_name").asText();
                    List<String> artists = new ArrayList<>();
                    artists.add(data.path("author_name").asText());
                    long duration = data.path("timelength").asLong();
                    String img = data.path("img").asText();
                    if (!img.startsWith("http")) {
                        img = "";
                    }
                    return new Music(musicId, name, artists, duration, PLATFORM, img);
                })
                .onErrorReturn(new Music(musicId, "", Collections.emptyList(), 0, PLATFORM, ""));
    }

    @Override
    public Mono<String> getPlayUrl(String musicId, String quality) {
        // Cookie 池：按序逐个尝试，失败自动切换下一个（酷狗接口无试听标记，空地址即视为失败）
        List<String> all = cookiePoolService.allEnabled(PLATFORM);
        final List<String> cookies = all.isEmpty() ? List.of("") : all;
        return Mono.defer(() -> {
            Mono<String> chain = Mono.error(new ApiRequestException("no cookie"));
            for (String cookie : cookies) {
                chain = chain.onErrorResume(e -> requestPlayUrl(musicId, cookie));
            }
            return chain;
        }).doOnSuccess(url -> {
            if (url != null && !url.isEmpty()) {
                String used = currentUsedCookie.get();
                if (used != null && !used.isBlank()) cookiePoolService.markSuccess(PLATFORM, used);
            }
        });
    }

    /** 按频道取播放地址：该频道手动选中的 Cookie 优先使用，失败再依次尝试其他启用 Cookie */
    @Override
    public Mono<String> getPlayUrl(String musicId, String quality, Long channelId) {
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
                chain = chain.onErrorResume(e -> requestPlayUrl(musicId, cookie));
            }
            return chain;
        }).doOnSuccess(url -> {
            if (url != null && !url.isEmpty()) {
                String used = currentUsedCookie.get();
                if (used != null && !used.isBlank()) cookiePoolService.markSuccess(PLATFORM, used);
            }
        });
    }

    private final java.util.concurrent.atomic.AtomicReference<String> currentUsedCookie = new java.util.concurrent.atomic.AtomicReference<>();

    private Mono<String> requestPlayUrl(String musicId, String cookie) {
        ensureConfigured();
        currentUsedCookie.set(cookie);
        log.info("getPlayUrl(kugou): musicId={} cookie=[{}...]", musicId,
                cookie == null ? "" : (cookie.length() > 24 ? cookie.substring(0, 24) : cookie));
        String hash = musicId;
        String albumAudioId = "0";
        if (musicId.contains("|")) {
            String[] parts = musicId.split("\\|");
            hash = parts[0];
            if (parts.length > 1) {
                albumAudioId = parts[1];
            }
        }
        return webClient.get()
                .uri(PLAY_URL + "?hash={hash}&album_audio_id={albumAudioId}", hash, albumAudioId)
                .header("Referer", "https://www.kugou.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookie != null && !cookie.isBlank() ? cookie : "kg_mid=2333")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    JsonNode data = jsonNode.path("data");
                    String url = data.path("play_backup_url").asText();
                    if (url.isEmpty()) {
                        url = data.path("play_url").asText();
                    }
                    if (url.isEmpty()) {
                        url = data.path("url").asText();
                    }
                    if (url == null || url.isEmpty()) {
                        // 空地址：该 Cookie 无权播放 → 标记失败并换下一个
                        cookiePoolService.markFailure(PLATFORM, cookie, "播放地址为空（Cookie 无权限）");
                        throw new ApiRequestException("播放地址为空（Cookie 无权限），尝试下一个");
                    }
                    log.info("getPlayUrl(kugou) result: musicId={} cookie=[{}...] url=OK", musicId,
                            cookie == null ? "" : (cookie.length() > 24 ? cookie.substring(0, 24) : cookie));
                    return url;
                })
                .onErrorResume(e -> {
                    if (e instanceof ApiRequestException) {
                        return Mono.error(e);
                    }
                    cookiePoolService.markFailure(PLATFORM, cookie, "酷狗取流请求失败: " + e.getMessage());
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<Music> getPlayableMusic(String musicId) {
        ensureConfigured();
        return getSongDetail(musicId);
    }

    @Override
    public Mono<List<Playlist>> getUserPlaylists(String userId) {
        ensureConfigured();
        return Mono.just(Collections.emptyList());
    }

    @Override
    public Mono<List<Music>> getPlaylistSongs(String playlistId, int offset, int limit) {
        ensureConfigured();
        return webClient.get()
                .uri("http://mobilecdn.kugou.com/api/v3/special/song?specialid={playlistId}&page=1&pagesize={limit}",
                        playlistId, Math.min(limit, 100))
                .header("Referer", "https://www.kugou.com/")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    List<Music> musicList = new ArrayList<>();
                    JsonNode data = jsonNode.path("data").path("info");
                    if (data.isArray()) {
                        data.forEach(song -> musicList.add(parseSongFromNode(song)));
                    }
                    return musicList;
                })
                .onErrorReturn(Collections.emptyList());
    }

    @Override
    public Mono<String> getLyric(String musicId) {
        return getSongDetail(musicId)
                .flatMap(music -> {
                    String timeLength = String.valueOf(music.duration());
                    String clientVer = "20001";
                    String dfid = "-";
                    String mid = "1";
                    String uuid = "1";

                    // L3：UriComponentsBuilder 组装（musicId 为用户可控的 hash，杜绝参数注入）
                    String params = org.springframework.web.util.UriComponentsBuilder.fromUriString(LRC_URL)
                            .queryParam("cmd", "200")
                            .queryParam("hash", musicId)
                            .queryParam("timelength", timeLength)
                            .queryParam("clientver", clientVer)
                            .queryParam("dfid", dfid)
                            .queryParam("mid", mid)
                            .queryParam("uuid", uuid)
                            .build(false)
                            .toUriString();

                    return webClient.get()
                            .uri(params)
                            .header("Referer", "https://www.kugou.com/")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .flatMap(lyricResult -> {
                                int status = lyricResult.path("status").asInt();
                                if (status != 200) {
                                    return Mono.just("");
                                }
                                JsonNode candidates = lyricResult.path("candidates");
                                if (candidates.isArray() && candidates.size() > 0) {
                                    String id = candidates.get(0).path("id").asText();
                                    String accesskey = candidates.get(0).path("accesskey").asText();
                                    long duration = candidates.get(0).path("duration").asLong();

                                    String dlParams = org.springframework.web.util.UriComponentsBuilder.fromUriString(LRC_DOWNLOAD_URL)
                                            .queryParam("id", id)
                                            .queryParam("accesskey", accesskey)
                                            .queryParam("fmt", "krc")
                                            .queryParam("charset", "utf8")
                                            .queryParam("kind", "1")
                                            .queryParam("clientver", clientVer)
                                            .queryParam("dfid", dfid)
                                            .queryParam("mid", mid)
                                            .queryParam("uuid", uuid)
                                            .build(false)
                                            .toUriString();

                                    return webClient.get()
                                            .uri(dlParams)
                                            .header("Referer", "https://www.kugou.com/")
                                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                            .retrieve()
                                            .bodyToMono(JsonNode.class)
                                            .map(dlResult -> {
                                                String content = dlResult.path("content").asText();
                                                if (content.isEmpty()) {
                                                    return "";
                                                }
                                                String decoded = decodeKrcToLrc(content);
                                                return decoded != null ? decoded : "";
                                            });
                                }
                                return Mono.just("");
                            });
                })
                .onErrorReturn("");
    }

    private String decodeKrcToLrc(String krcContent) {
        try {
            byte[] krcBytes = krcContent.getBytes(StandardCharsets.ISO_8859_1);
            if (krcBytes.length < 4) return null;

            byte[] encrypted = new byte[krcBytes.length - 4];
            System.arraycopy(krcBytes, 4, encrypted, 0, encrypted.length);

            for (int i = 0; i < encrypted.length; i++) {
                encrypted[i] ^= XOR_KEY[i % XOR_KEY.length];
            }

            Inflater inflater = new Inflater();
            inflater.setInput(encrypted);
            byte[] result = new byte[encrypted.length * 3];
            int len = inflater.inflate(result);
            inflater.end();

            byte[] decompressed = new byte[len];
            System.arraycopy(result, 0, decompressed, 0, len);

            return new String(decompressed, StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            log.error("Failed to decode KRC lyrics", e);
            return null;
        }
    }

    @Override
    public Mono<List<UserSearchResult>> searchUsers(String keyword) {
        ensureConfigured();
        return Mono.just(Collections.emptyList());
    }

    @Override
    public boolean isEnabled() {
        return appProperties.getMusicApi().getKugou().isEnabled();
    }

    @Override
    public void setCookie(String cookie) {
        if (org.springframework.util.StringUtils.hasText(cookie)) {
            cookiePoolService.add("kugou", cookie, null);
        }
        log.info("KugouMusic cookie updated.");
    }

    @Override
    public String getCookie() {
        return cookiePoolService.next("kugou");
    }
}
