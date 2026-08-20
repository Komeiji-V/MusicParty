package org.thornex.musicparty.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.dto.Music;
import org.thornex.musicparty.dto.Playlist;
import org.thornex.musicparty.dto.UserSearchResult;
import org.thornex.musicparty.enums.CacheStatus;
import org.thornex.musicparty.exception.ApiRequestException;
import org.thornex.musicparty.service.CookiePoolService;
import org.thornex.musicparty.event.DownloadStatusEvent;
import org.thornex.musicparty.service.LocalCacheService;
import org.thornex.musicparty.util.BilibiliApiUtils;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.*;
import java.util.stream.StreamSupport;

@Component
@Slf4j
public class BilibiliMusicProvider implements MusicProvider {

    private final WebClient webClient;
    private final AppProperties appProperties;
    private final LocalCacheService localCacheService;
    private final BilibiliWbiService wbiService;
    private final ApplicationEventPublisher eventPublisher;
    private final CookiePoolService cookiePoolService;
    private static final String PLATFORM = "bilibili";

    private static class WbiSignatureException extends RuntimeException {
        public WbiSignatureException(String message) { super(message); }
    }

    public BilibiliMusicProvider(WebClient webClient, AppProperties appProperties,
                                  LocalCacheService localCacheService, BilibiliWbiService wbiService,
                                  ApplicationEventPublisher eventPublisher,
                                  CookiePoolService cookiePoolService) {
        this.webClient = webClient;
        this.appProperties = appProperties;
        this.localCacheService = localCacheService;
        this.wbiService = wbiService;
        this.eventPublisher = eventPublisher;
        this.cookiePoolService = cookiePoolService;
        if (org.springframework.util.StringUtils.hasText(appProperties.getMusicApi().getBilibili().getSessdata())) {
            log.info("Bilibili env SESSDATA will be seeded into pool");
        }
    }

    private String getBaseUrl() {
        return appProperties.getMusicApi().getBilibili().getBaseUrl();
    }

    private void ensureConfigured() {
        if (!org.springframework.util.StringUtils.hasText(getSessdata())) {
            throw new ApiRequestException("尚未配置 Bilibili SESSDATA，请联系管理员设置");
        }
    }

    public void updateSessdata(String newSessdata) {
        if (org.springframework.util.StringUtils.hasText(newSessdata)) {
            cookiePoolService.add("bilibili", newSessdata, null);
        }
        this.wbiService.updateSessdata(newSessdata);
        log.info("Bilibili API Service SESSDATA updated (cookie pool).");
    }

    private String getSessdata() {
        return cookiePoolService.next("bilibili");
    }

    private WebClient.RequestHeadersSpec<?> buildBilibiliRequest(String uri) {
        return webClient.get()
                .uri(uri)
                .header("Cookie", "SESSDATA=" + getSessdata())
                .header("Referer", "https://www.bilibili.com/");
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public Mono<List<Music>> searchMusic(String keyword, int limit, int offset) {
        ensureConfigured();
        int page = (offset / Math.max(limit, 1)) + 1;

        Map<String, String> params = new HashMap<>();
        params.put("search_type", "video");
        params.put("keyword", keyword);
        params.put("page", String.valueOf(page));
        params.put("page_size", String.valueOf(Math.min(limit, 20)));

        Mono<List<Music>> requestMono = wbiService.signParams(params)
                .flatMap(signedParams -> {
                    UriComponentsBuilder builder = UriComponentsBuilder
                            .fromHttpUrl(getBaseUrl() + "/x/web-interface/wbi/search/type");
                    signedParams.forEach(builder::queryParam);

                    return webClient.get()
                            .uri(builder.build().toUri())
                            .header("Cookie", "SESSDATA=" + getSessdata())
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Referer", "https://www.bilibili.com/")
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .handle((json, sink) -> {
                                int code = json.path("code").asInt();
                                if (code == -403 || code == -400) {
                                    sink.error(new WbiSignatureException("WBI signature invalid, code: " + code));
                                    return;
                                }
                                if (code != 0) {
                                    log.error("Bilibili search failed: {}", json.path("message").asText());
                                    sink.next(Collections.emptyList());
                                    return;
                                }
                                List<Music> musicList = new ArrayList<>();
                                JsonNode results = json.path("data").path("result");
                                if (results.isArray()) {
                                    results.forEach(video -> {
                                        String rawTitle = video.path("title").asText();
                                        String cleanTitle = rawTitle.replaceAll("<[^>]*>", "");
                                        String durationStr = video.path("duration").asText();
                                        long durationMs = BilibiliApiUtils.durationToMillis(durationStr);
                                        String picUrl = video.path("pic").asText();
                                        if (!picUrl.startsWith("http")) {
                                            picUrl = "https:" + picUrl;
                                        }
                                        musicList.add(new Music(
                                                video.path("bvid").asText(),
                                                cleanTitle,
                                                List.of(video.path("author").asText()),
                                                durationMs,
                                                PLATFORM,
                                                picUrl));
                                    });
                                }
                                sink.next(musicList);
                            });
                });

        return requestMono.retryWhen(Retry.max(1)
                        .filter(throwable -> throwable instanceof WbiSignatureException)
                        .doBeforeRetry(retrySignal -> {
                            log.warn("Detected WBI signature error, refreshing key and retrying...");
                            wbiService.invalidateCache();
                        }))
                .onErrorResume(WbiSignatureException.class, e -> {
                    log.error("Bilibili search failed after retry: {}", e.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<Music> getSongDetail(String musicId) {
        ensureConfigured();
        return BilibiliApiUtils.getVideoDetails(musicId, webClient, getBaseUrl(), getSessdata());
    }

    @Override
    public Mono<String> getPlayUrl(String musicId, String quality) {
        ensureConfigured();
        return resolveDashAudioUrl(musicId);
    }

    @Override
    public Mono<Music> getPlayableMusic(String musicId) {
        ensureConfigured();
        String localUrl = localCacheService.getLocalUrl(musicId);
        if (localUrl != null) {
            return getSongDetail(musicId);
        } else {
            prefetchMusic(musicId);
            return getSongDetail(musicId);
        }
    }

    public void prefetchMusic(String bvid) {
        ensureConfigured();
        CacheStatus status = localCacheService.getStatus(bvid);
        if (status == CacheStatus.COMPLETED || status == CacheStatus.DOWNLOADING) {
            return;
        }
        log.info("Prefetching Bilibili music: {}", bvid);

        Mono<String> urlProvider = resolveDashAudioUrl(bvid);
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.bilibili.com/video/" + bvid);
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        localCacheService.submitDownload(bvid, urlProvider, headers, ".m4a");
    }

    private Mono<String> resolveDashAudioUrl(String bvid) {
        return BilibiliApiUtils.getVideoCid(bvid, webClient, getBaseUrl(), getSessdata())
                .flatMap(cid -> {
                    Map<String, String> params = new HashMap<>();
                    params.put("bvid", bvid);
                    params.put("cid", cid);
                    params.put("fnval", "16");

                    return wbiService.signParams(params)
                            .flatMap(signedParams -> {
                                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(getBaseUrl() + "/x/player/wbi/playurl");
                                signedParams.forEach(builder::queryParam);

                                return webClient.get()
                                        .uri(builder.build().toUri())
                                        .header("Cookie", "SESSDATA=" + getSessdata())
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                        .header("Referer", "https://www.bilibili.com/video/" + bvid)
                                        .retrieve()
                                        .bodyToMono(JsonNode.class)
                                        .flatMap(jsonNode -> {
                                            int code = jsonNode.path("code").asInt();
                                            if (code == -403 || code == -400) {
                                                return Mono.error(new WbiSignatureException("Invalid WBI signature, code: " + code));
                                            }
                                            if (code != 0) {
                                                return Mono.error(new ApiRequestException("Bilibili API Error, code: " + code));
                                            }
                                            JsonNode audioStreams = jsonNode.path("data").path("dash").path("audio");
                                            if (audioStreams.isMissingNode()) {
                                                return Mono.error(new ApiRequestException("No DASH audio found"));
                                            }
                                            String url = StreamSupport.stream(audioStreams.spliterator(), false)
                                                    .max(Comparator.comparingInt(a -> a.path("id").asInt()))
                                                    .map(a -> a.path("baseUrl").asText())
                                                    .orElseThrow(() -> new ApiRequestException("No audio url found in json"));
                                            return Mono.just(url);
                                        });
                            })
                            .retryWhen(Retry.max(1)
                                    .filter(throwable -> throwable instanceof WbiSignatureException)
                                    .doBeforeRetry(retrySignal -> {
                                        log.warn("WBI signature error on getting play url. Invalidating cache and retrying...");
                                        wbiService.invalidateCache();
                                    }));
                });
    }

    @Override
    public Mono<List<Playlist>> getUserPlaylists(String userId) {
        ensureConfigured();
        String favListApi = getBaseUrl() + "/x/v3/fav/folder/created/list-all";
        String uri = UriComponentsBuilder.fromHttpUrl(favListApi)
                .queryParam("up_mid", userId)
                .build()
                .toUriString();

        return buildBilibiliRequest(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    if (jsonNode.path("code").asInt() != 0) {
                        log.warn("Failed to get Bilibili favorites for user {}: {}", userId, jsonNode.path("message").asText());
                        return Collections.<Playlist>emptyList();
                    }
                    List<Playlist> playlists = new ArrayList<>();
                    JsonNode list = jsonNode.path("data").path("list");
                    if (list.isArray()) {
                        list.forEach(fav -> {
                            int count = fav.path("media_count").asInt();
                            if (count > 0) {
                                playlists.add(new Playlist(
                                        fav.path("id").asText(),
                                        fav.path("title").asText(),
                                        fav.path("cover").asText(),
                                        count,
                                        PLATFORM
                                ));
                            }
                        });
                    }
                    return playlists;
                })
                .onErrorReturn(Collections.<Playlist>emptyList());
    }

    @Override
    public Mono<List<Music>> getPlaylistSongs(String playlistId, int offset, int limit) {
        ensureConfigured();
        int safeLimit = Math.min(limit, 20);
        int pageNumber = (offset / safeLimit) + 1;

        String favDetailApi = getBaseUrl() + "/x/v3/fav/resource/list";
        String uri = UriComponentsBuilder.fromHttpUrl(favDetailApi)
                .queryParam("media_id", playlistId)
                .queryParam("ps", safeLimit)
                .queryParam("pn", pageNumber)
                .build()
                .toUriString();

        return buildBilibiliRequest(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    List<Music> musicList = new ArrayList<>();
                    int code = jsonNode.path("code").asInt();
                    if (code != 0) {
                        if (code == -404) return musicList;
                        log.error("Failed to get Bilibili favorite details: {}", jsonNode.path("message").asText());
                        return musicList;
                    }
                    JsonNode medias = jsonNode.path("data").path("medias");
                    if (medias.isArray()) {
                        medias.forEach(media -> {
                            String title = media.path("title").asText();
                            if ("已失效视频".equals(title)) {
                                musicList.add(new Music("INVALID_SKIP", "已失效视频",
                                        List.of("Unknown"), 0, PLATFORM, ""));
                                return;
                            }
                            musicList.add(new Music(
                                    media.path("bvid").asText(),
                                    title,
                                    List.of(media.path("upper").path("name").asText()),
                                    media.path("duration").asLong() * 1000,
                                    PLATFORM,
                                    media.path("cover").asText()
                            ));
                        });
                    }
                    return musicList;
                });
    }

    @Override
    public Mono<List<UserSearchResult>> searchUsers(String keyword) {
        ensureConfigured();
        Map<String, String> params = new HashMap<>();
        params.put("search_type", "bili_user");
        params.put("keyword", keyword);

        return wbiService.signParams(params)
                .flatMap(signedParams -> {
                    UriComponentsBuilder builder = UriComponentsBuilder
                            .fromHttpUrl(getBaseUrl() + "/x/web-interface/wbi/search/type");
                    signedParams.forEach(builder::queryParam);

                    return webClient.get()
                            .uri(builder.build().toUri())
                            .header("Cookie", "SESSDATA=" + getSessdata())
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Referer", "https://www.bilibili.com/")
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(jsonNode -> {
                                List<UserSearchResult> users = new ArrayList<>();
                                if (jsonNode.path("code").asInt() != 0) {
                                    log.error("Bilibili user search failed: {}", jsonNode.path("message").asText());
                                    return users;
                                }
                                JsonNode results = jsonNode.path("data").path("result");
                                if (results.isArray()) {
                                    results.forEach(u -> {
                                        String pic = u.path("upic").asText();
                                        if (!pic.startsWith("http")) {
                                            pic = "https:" + pic;
                                        }
                                        users.add(new UserSearchResult(
                                                u.path("mid").asText(),
                                                u.path("uname").asText(),
                                                pic,
                                                PLATFORM
                                        ));
                                    });
                                }
                                return users;
                            });
                });
    }

    @Override
    public Mono<String> getLyric(String musicId) {
        return Mono.just("");
    }

    @Override
    public boolean isEnabled() {
        return appProperties.getMusicApi().getBilibili().isEnabled();
    }

    @Override
    public void setCookie(String cookie) {
        if (org.springframework.util.StringUtils.hasText(cookie)) {
            cookiePoolService.add("bilibili", cookie, null);
        }
        log.info("Bilibili SESSDATA added to cookie pool.");
    }

    @Override
    public String getCookie() {
        return getSessdata();
    }
}
