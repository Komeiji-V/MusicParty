package org.thornex.musicparty.service.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class QQMusicProvider implements MusicProvider {

    private final WebClient webClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final CookiePoolService cookiePoolService;
    private static final String PLATFORM = "qq";
    private static final String SEARCH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String GET_SONG_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String PLAYLIST_URL = "https://c.y.qq.com/qzone/fcgi-bin/fcg_uccreate_get_playlistinfo.fcg";

    public QQMusicProvider(WebClient webClient, AppProperties appProperties, CookiePoolService cookiePoolService) {
        this.webClient = webClient;
        this.appProperties = appProperties;
        this.objectMapper = new ObjectMapper();
        this.cookiePoolService = cookiePoolService;
    }

    @PostConstruct
    public void initialize() {
        if (StringUtils.hasText(appProperties.getMusicApi().getQq().getCookie())) {
            log.info("QQMusic provider initialized with cookie.");
        } else {
            log.info("QQMusic provider initialized without cookie (limited functionality).");
        }
    }

    private String getBaseUrl() {
        return appProperties.getMusicApi().getQq().getBaseUrl();
    }

    private String getDefaultQuality() {
        return appProperties.getMusicApi().getQq().getQuality();
    }

    private void ensureConfigured() {
        if (!appProperties.getMusicApi().getQq().isEnabled()) {
            throw new ApiRequestException("QQ音乐源已被禁用");
        }
    }

    private int getGtk(String cookie) {
        if (cookie == null || cookie.isEmpty()) return 0;
        String skey = extractCookieValue(cookie, "skey");
        if (skey == null || skey.isEmpty()) {
            skey = extractCookieValue(cookie, "p_skey");
        }
        if (skey == null || skey.isEmpty()) return 0;
        int hash = 5381;
        for (int i = 0; i < skey.length(); i++) {
            hash += (hash << 5) + skey.charAt(i);
        }
        return hash & 0x7fffffff;
    }

    private String extractCookieValue(String cookie, String key) {
        Pattern pattern = Pattern.compile(Pattern.quote(key) + "=([^;]*)");
        Matcher matcher = pattern.matcher(cookie);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private Mono<JsonNode> postMusicu(Map<String, Object> reqData) {
        return postMusicu(reqData, getCookie());
    }

    private Mono<JsonNode> postMusicu(Map<String, Object> reqData, String cookie) {
        try {
            String body = objectMapper.writeValueAsString(reqData);
            return webClient.post()
                    .uri(SEARCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Referer", "https://y.qq.com/")
                    .header("Cookie", cookie != null ? cookie : "")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class);
        } catch (JsonProcessingException e) {
            return Mono.error(new ApiRequestException("Failed to serialize QQ music request"));
        }
    }

    private ObjectNode buildMusicuReq(String module, String method, ObjectNode param) {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("module", module);
        req.put("method", method);
        req.set("param", param);
        return req;
    }

    private Map<String, Object> buildComm() {
        return buildComm(getCookie());
    }

    private Map<String, Object> buildComm(String cookie) {
        Map<String, Object> comm = new HashMap<>();
        // 从 Cookie 提取 uin（绿钻授权需要真实 uin），无则 0
        String uinStr = cookie != null ? extractCookieValue(cookie, "uin") : "";
        long uin = 0;
        if (uinStr != null && !uinStr.isEmpty()) {
            try {
                uin = Long.parseLong(uinStr);
            } catch (NumberFormatException ignored) {
            }
        }
        comm.put("uin", uin);
        comm.put("format", "json");
        comm.put("ct", 24);
        comm.put("cv", 0);
        comm.put("g_tk", getGtk(cookie));
        return comm;
    }

    private String generateGuid() {
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 16; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private Mono<JsonNode> getWithReferer(String url, Map<String, String> params) {
        String queryString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        String fullUrl = url + "?" + queryString;

        return webClient.get()
                .uri(fullUrl)
                .header("Referer", "https://y.qq.com/")
                .header("Cookie", getCookie())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Music parseSongFromNode(JsonNode song) {
        String songMid = song.path("songmid").asText();
        if (songMid.isEmpty()) {
            songMid = song.path("mid").asText();
        }
        String name = song.path("songname").asText();
        if (name.isEmpty()) {
            name = song.path("name").asText();
        }

        List<String> artists = new ArrayList<>();
        JsonNode singerArray = song.has("singer") ? song.path("singer") : null;
        if (singerArray != null && singerArray.isArray()) {
            singerArray.forEach(s -> artists.add(s.path("name").asText()));
        }

        long duration = song.path("interval").asLong() * 1000L;

        String albumMid = song.has("albummid") ? song.path("albummid").asText() : "";
        String coverUrl = "";
        if (!albumMid.isEmpty()) {
            coverUrl = "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
        }

        return new Music(songMid, name, artists, duration, PLATFORM, coverUrl);
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public Mono<List<Music>> searchMusic(String keyword, int limit, int offset) {
        ensureConfigured();
        int pageNo = (offset / Math.max(limit, 1)) + 1;

        ObjectNode songParam = objectMapper.createObjectNode();
        songParam.put("grp", 1);
        songParam.put("num_per_page", limit);
        songParam.put("page_num", pageNo);
        songParam.put("query", keyword);
        songParam.put("search_type", 0);
        songParam.put("loginUin", 0);
        songParam.put("hostUin", 0);
        songParam.put("inCharset", "utf8");
        songParam.put("outCharset", "utf-8");
        songParam.put("format", "json");
        songParam.put("needNewCode", 1);

        Map<String, Object> reqData = new HashMap<>();
        reqData.put("comm", buildComm());
        reqData.put("req_0", buildMusicuReq("music.search.SearchCgiService", "DoSearchForQQMusicDesktop", songParam));

        return postMusicu(reqData)
                .map(jsonNode -> {
                    List<Music> musicList = new ArrayList<>();
                    JsonNode data = jsonNode.path("req_0").path("data");
                    JsonNode body = data.path("body");
                    JsonNode songList = body.path("song").path("list");
                    if (songList.isArray()) {
                        songList.forEach(song -> musicList.add(parseSongFromNode(song.path("songInfo"))));
                    }
                    return musicList;
                });
    }

    @Override
    public Mono<Music> getSongDetail(String musicId) {
        ensureConfigured();
        ObjectNode detailParam = objectMapper.createObjectNode();
        detailParam.put("song_id", 0);
        detailParam.put("song_mid", musicId);
        detailParam.put("song_type", 1);

        Map<String, Object> reqData = new HashMap<>();
        reqData.put("comm", buildComm());
        reqData.put("req_0", buildMusicuReq("music.pf_song_detail_svr", "get_song_detail_yqq", detailParam));

        return postMusicu(reqData)
                .map(jsonNode -> {
                    JsonNode data = jsonNode.path("req_0").path("data");
                    JsonNode trackInfo = data.path("track_info");
                    if (trackInfo.isMissingNode() || trackInfo.isNull()) {
                        throw new ApiRequestException("未找到歌曲详情: " + musicId);
                    }
                    return parseSongFromNode(trackInfo);
                });
    }

    /** 按专辑名搜索专辑并返回其全部歌曲（含专辑 ID，供跳转官方页） */
    public Mono<Map<String, Object>> getAlbumSongs(String albumName) {
        ensureConfigured();
        ObjectNode albumParam = objectMapper.createObjectNode();
        albumParam.put("grp", 1);
        albumParam.put("num_per_page", 5);
        albumParam.put("page_num", 1);
        albumParam.put("query", albumName);
        albumParam.put("search_type", 2); // 2 = 专辑
        albumParam.put("loginUin", 0);
        albumParam.put("hostUin", 0);
        albumParam.put("inCharset", "utf8");
        albumParam.put("outCharset", "utf-8");
        albumParam.put("format", "json");
        albumParam.put("needNewCode", 1);

        Map<String, Object> reqData = new HashMap<>();
        reqData.put("comm", buildComm());
        reqData.put("req_0", buildMusicuReq("music.search.SearchCgiService", "DoSearchForQQMusicDesktop", albumParam));

        return postMusicu(reqData)
                .flatMap(jsonNode -> {
                    JsonNode list = jsonNode.path("req_0").path("data").path("body").path("album").path("list");
                    if (!list.isArray() || list.isEmpty()) return Mono.just(emptyAlbum());
                    JsonNode first = list.get(0);
                    String albumMidRaw = first.path("mid").asText("");
                    if (albumMidRaw.isEmpty()) albumMidRaw = first.path("albumMid").asText("");
                    final String albumMid = albumMidRaw;
                    final String albumNameResolved = first.path("name").asText(albumName);
                    if (albumMid.isEmpty()) return Mono.just(emptyAlbum());
                    log.info("QQ Album [{}] resolved to mid={}", albumNameResolved, albumMid);

                    ObjectNode detailParam = objectMapper.createObjectNode();
                    detailParam.put("albumMid", albumMid);
                    Map<String, Object> detailReq = new HashMap<>();
                    detailReq.put("comm", buildComm());
                    detailReq.put("req_0", buildMusicuReq("music.musicasset.AlbumDetailGet", "GetAlbumDetail", detailParam));

                    return postMusicu(detailReq).map(detail -> {
                        JsonNode data = detail.path("req_0").path("data");
                        List<Music> songs = new ArrayList<>();
                        JsonNode trackList = data.path("track").path("list");
                        if (trackList.isArray()) {
                            trackList.forEach(song -> songs.add(parseSongFromNode(song)));
                        }
                        Map<String, Object> result = new HashMap<>();
                        result.put("id", albumMid);
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
    public Mono<String> getPlayUrl(String musicId, String quality) {
        String qualityStr = quality != null ? quality : getDefaultQuality();
        // Cookie 池：按序逐个尝试，失败自动切换下一个
        List<String> all = cookiePoolService.allEnabled("qq");
        final List<String> cookies = all.isEmpty() ? List.of("") : all;
        return Mono.defer(() -> {
            Mono<String> chain = Mono.error(new ApiRequestException("no cookie"));
            for (String cookie : cookies) {
                chain = chain.onErrorResume(e -> requestPlayUrl(musicId, qualityStr, cookie));
            }
            return chain;
        }).doOnSuccess(url -> {
            if (url != null && !url.isEmpty()) {
                String used = currentUsedCookie.get();
                if (used != null && !used.isBlank()) cookiePoolService.markSuccess("qq", used);
            }
        });
    }

    /** 按频道取播放地址：该频道手动选中的 Cookie 优先使用，失败再依次尝试其他启用 Cookie */
    @Override
    public Mono<String> getPlayUrl(String musicId, String quality, Long channelId) {
        String qualityStr = quality != null ? quality : getDefaultQuality();
        List<String> cookies = new ArrayList<>();
        String selected = channelId != null ? cookiePoolService.getSelectedCookie(channelId, "qq") : null;
        if (selected != null && !selected.isBlank()) {
            cookies.add(selected);
        }
        for (String c : cookiePoolService.allEnabled("qq")) {
            if (!cookies.contains(c)) cookies.add(c);
        }
        if (cookies.isEmpty()) cookies.add("");
        final List<String> chainCookies = cookies;
        return Mono.defer(() -> {
            Mono<String> chain = Mono.error(new ApiRequestException("no cookie"));
            for (String cookie : chainCookies) {
                chain = chain.onErrorResume(e -> requestPlayUrl(musicId, qualityStr, cookie));
            }
            return chain;
        }).doOnSuccess(url -> {
            if (url != null && !url.isEmpty()) {
                String used = currentUsedCookie.get();
                if (used != null && !used.isBlank()) cookiePoolService.markSuccess("qq", used);
            }
        });
    }

    private final java.util.concurrent.atomic.AtomicReference<String> currentUsedCookie = new java.util.concurrent.atomic.AtomicReference<>();

    private Mono<String> requestPlayUrl(String musicId, String qualityStr, String cookie) {
        ensureConfigured();
        currentUsedCookie.set(cookie);
        log.info("getPlayUrl(qq): musicId={} cookie=[{}...]", musicId,
                org.thornex.musicparty.util.CryptoUtil.mask(cookie));

        ObjectNode urlParam = objectMapper.createObjectNode();
        ArrayNode mids = objectMapper.createArrayNode();
        mids.add(musicId);
        urlParam.set("songmid", mids);
        urlParam.put("filename", "M500" + musicId + ".mp3");
        urlParam.put("songtype", 1);
        urlParam.put("guid", generateGuid());
        urlParam.put("uin", 0);
        urlParam.put("platform", 20);
        urlParam.put("needNewCode", 0);

        Map<String, Object> reqData = new HashMap<>();
        reqData.put("comm", buildComm(cookie));
        reqData.put("req_0", buildMusicuReq("vkey.GetVkeyServer", "CgiGetVkey", urlParam));

        return postMusicu(reqData, cookie)
                .map(jsonNode -> {
                    JsonNode urlData = jsonNode.path("req_0").path("data");
                    JsonNode midurlinfo = urlData.path("midurlinfo");
                    if (midurlinfo.isArray() && midurlinfo.size() > 0) {
                        JsonNode first = midurlinfo.get(0);
                        String purl = first.path("purl").asText();
                        JsonNode sip = urlData.path("sip");
                        if (!purl.isEmpty() && sip.isArray() && sip.size() > 0) {
                            log.info("getPlayUrl(qq) result: musicId={} cookie=[{}...] url=OK", musicId,
                                    org.thornex.musicparty.util.CryptoUtil.mask(cookie));
                            return sip.get(0).asText() + purl;
                        }
                    }
                    // 空地址：该 Cookie 无权播放（VIP/绿钻限制）→ 标记失败并换下一个
                    cookiePoolService.markFailure("qq", cookie, "播放地址为空（Cookie 无 VIP 权限）");
                    throw new ApiRequestException("播放地址为空（Cookie 无权限），尝试下一个");
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
        Map<String, String> params = new HashMap<>();
        params.put("type", "1");
        params.put("json", "1");
        params.put("utf8", "1");
        params.put("onlysong", "0");
        params.put("new_format", "1");
        params.put("nosign", "1");
        params.put("discover", "0");
        params.put("g_tk", String.valueOf(getGtk(getCookie())));

        return getWithReferer(PLAYLIST_URL, params)
                .map(jsonNode -> {
                    List<Playlist> playlists = new ArrayList<>();
                    JsonNode list = jsonNode.path("cdlist");
                    if (list.isArray()) {
                        list.forEach(pl -> playlists.add(new Playlist(
                                pl.path("dissid").asText(),
                                pl.path("dissname").asText(),
                                pl.path("logo").asText(),
                                pl.path("songnum").asInt(),
                                PLATFORM
                        )));
                    }
                    return playlists;
                })
                .onErrorReturn(Collections.emptyList());
    }

    @Override
    public Mono<List<Music>> getPlaylistSongs(String playlistId, int offset, int limit) {
        ensureConfigured();
        Map<String, String> params = new HashMap<>();
        params.put("type", "1");
        params.put("json", "1");
        params.put("utf8", "1");
        params.put("onlysong", "0");
        params.put("new_format", "1");
        params.put("nosign", "1");
        params.put("disstid", playlistId);
        params.put("g_tk", String.valueOf(getGtk(getCookie())));

        return getWithReferer(PLAYLIST_URL, params)
                .map(jsonNode -> {
                    List<Music> musicList = new ArrayList<>();
                    JsonNode cdlist = jsonNode.path("cdlist");
                    if (cdlist.isArray() && cdlist.size() > 0) {
                        JsonNode songlist = cdlist.get(0).path("songlist");
                        if (songlist.isArray()) {
                            int start = offset;
                            int end = Math.min(offset + limit, songlist.size());
                            for (int i = start; i < end; i++) {
                                JsonNode song = songlist.get(i);
                                String songMid = song.path("songmid").asText();
                                String name = song.path("songname").asText();
                                List<String> artists = new ArrayList<>();
                                JsonNode singerArray = song.path("singer");
                                if (singerArray.isArray()) {
                                    singerArray.forEach(s -> artists.add(s.path("name").asText()));
                                }
                                long duration = song.path("interval").asLong() * 1000L;
                                String albumMid = song.path("albummid").asText();
                                String coverUrl = "";
                                if (!albumMid.isEmpty()) {
                                    coverUrl = "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
                                }
                                musicList.add(new Music(songMid, name, artists, duration, PLATFORM, coverUrl));
                            }
                        }
                    }
                    return musicList;
                })
                .onErrorReturn(Collections.emptyList());
    }

    @Override
    public Mono<String> getLyric(String musicId) {
        ObjectNode lyricParam = objectMapper.createObjectNode();
        lyricParam.put("songMID", musicId);
        lyricParam.put("songID", 0);
        lyricParam.put("albumMID", "");
        lyricParam.put("songUID", 0);
        lyricParam.put("trans", 1);
        lyricParam.put("format", "json");
        lyricParam.put("platform", "yqq.json");
        lyricParam.put("cv", 8080307);
        lyricParam.put("ct", 24);

        Map<String, Object> reqData = new HashMap<>();
        reqData.put("comm", buildComm());
        reqData.put("req_0", buildMusicuReq("music.musichallSong.PlayLyricInfo.GetPlayLyricInfo", "GetPlayLyricInfo", lyricParam));

        return postMusicu(reqData)
                .map(jsonNode -> {
                    JsonNode lyricData = jsonNode.path("req_0").path("data");
                    String lyric = lyricData.path("lyric").asText();
                    if (lyric.isEmpty()) {
                        lyric = lyricData.path("trans").asText();
                    }
                    return decodeBase64IfNeeded(lyric);
                })
                .onErrorReturn("");
    }

    private String decodeBase64IfNeeded(String content) {
        if (content == null || content.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return content;
        }
    }

    @Override
    public Mono<List<UserSearchResult>> searchUsers(String keyword) {
        ensureConfigured();
        ObjectNode userParam = objectMapper.createObjectNode();
        userParam.put("grp", 1);
        userParam.put("num_per_page", 20);
        userParam.put("page_num", 1);
        userParam.put("query", keyword);
        userParam.put("search_type", 100);
        userParam.put("loginUin", 0);
        userParam.put("hostUin", 0);
        userParam.put("inCharset", "utf8");
        userParam.put("outCharset", "utf-8");
        userParam.put("format", "json");
        userParam.put("needNewCode", 1);

        Map<String, Object> reqData = new HashMap<>();
        reqData.put("comm", buildComm());
        reqData.put("req_0", buildMusicuReq("music.search.SearchCgiService", "DoSearchForQQMusicDesktop", userParam));

        return postMusicu(reqData)
                .map(jsonNode -> {
                    List<UserSearchResult> users = new ArrayList<>();
                    JsonNode data = jsonNode.path("req_0").path("data");
                    JsonNode body = data.path("body");
                    JsonNode userList = body.path("user").path("list");
                    if (userList.isArray()) {
                        userList.forEach(u -> {
                            JsonNode userInfo = u.path("userInfo");
                            users.add(new UserSearchResult(
                                    userInfo.path("encrypt_uin").asText(),
                                    userInfo.path("nick").asText(),
                                    userInfo.path("headurl").asText(),
                                    PLATFORM
                            ));
                        });
                    }
                    return users;
                })
                .onErrorReturn(Collections.emptyList());
    }

    @Override
    public boolean isEnabled() {
        return appProperties.getMusicApi().getQq().isEnabled();
    }

    @Override
    public void setCookie(String cookie) {
        if (StringUtils.hasText(cookie)) {
            cookiePoolService.add("qq", cookie, null);
        }
        log.info("QQMusic cookie updated.");
    }

    @Override
    public String getCookie() {
        return cookiePoolService.next("qq");
    }
}
