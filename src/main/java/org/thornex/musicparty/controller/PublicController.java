package org.thornex.musicparty.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.thornex.musicparty.dto.Music;
import org.thornex.musicparty.dto.NowPlayingInfo;
import org.thornex.musicparty.entity.Channel;
import org.thornex.musicparty.entity.Channel.JoinPermission;
import org.thornex.musicparty.entity.TitleDef;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.entity.UserPlaylist;
import org.thornex.musicparty.exception.ApiRequestException;
import org.thornex.musicparty.repository.ChannelRepository;
import org.thornex.musicparty.repository.LikeRecordRepository;
import org.thornex.musicparty.repository.PlaylistItemRepository;
import org.thornex.musicparty.repository.TitleDefRepository;
import org.thornex.musicparty.repository.UserPlaylistRepository;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.repository.UserTitleRepository;
import org.thornex.musicparty.service.ChannelSessionManager;
import org.thornex.musicparty.service.MusicPlayerService;
import org.thornex.musicparty.service.MusicQueueManager;
import org.thornex.musicparty.service.SystemConfigService;
import org.thornex.musicparty.service.api.NeteaseMusicProvider;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private static final int RECENT_PLAYS_LIMIT = 10;

    private final SystemConfigService systemConfigService;
    private final ChannelRepository channelRepository;
    private final ChannelSessionManager channelSessionManager;
    private final MusicPlayerService musicPlayerService;
    private final MusicQueueManager musicQueueManager;
    private final LikeRecordRepository likeRecordRepository;
    private final UserRepository userRepository;
    private final UserTitleRepository titleRepository;
    private final TitleDefRepository titleDefRepository;
    private final UserPlaylistRepository playlistRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final NeteaseMusicProvider neteaseMusicProvider;
    private final org.thornex.musicparty.service.api.QQMusicProvider qqMusicProvider;
    private final org.thornex.musicparty.service.api.KugouMusicProvider kugouMusicProvider;
    private final org.thornex.musicparty.util.IpRateLimiter ipRateLimiter;

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> result = new HashMap<>();
        result.put("siteTitle", systemConfigService.getSiteTitle());
        result.put("authorName", systemConfigService.getAuthorName());
        result.put("backWords", systemConfigService.getBackWords());
        result.put("hasInfoPage", systemConfigService.hasInfoPage());
        return result;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = systemConfigService.getStats();
        Map<String, Object> result = new HashMap<>();
        result.put("totalChannels", stats.get("totalChannels"));
        result.put("onlineUsers", stats.get("onlineUsers"));
        result.put("totalSongs", stats.get("playHistory"));
        result.put("totalUsers", stats.get("totalUsers"));
        return result;
    }

    @GetMapping("/channels")
    public List<Map<String, Object>> listChannels() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Channel channel : channelRepository.findAll()) {
            JoinPermission permission = resolvePermission(channel);
            if (permission != JoinPermission.PUBLIC && permission != JoinPermission.PASSWORD) {
                continue;
            }
            result.add(buildChannelCard(channel));
        }
        return result;
    }

    @GetMapping("/channels/{id}")
    public Map<String, Object> channelDetail(@PathVariable Long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ApiRequestException("频道不存在"));
        JoinPermission permission = resolvePermission(channel);
        if (permission == JoinPermission.INVITE_ONLY || permission == JoinPermission.HIDDEN) {
            throw new ApiRequestException("频道不存在");
        }
        Map<String, Object> result = buildChannelCard(channel);
        result.put("isPaused", musicPlayerService.isChannelPaused(id));

        List<Map<String, Object>> recentPlays = new ArrayList<>();
        List<Music> history = musicQueueManager.getHistorySnapshot(id);
        int limit = Math.min(history.size(), RECENT_PLAYS_LIMIT);
        for (int i = 0; i < limit; i++) {
            Music m = history.get(i);
            recentPlays.add(Map.of(
                    "name", m.name(),
                    "artists", m.artists(),
                    "coverUrl", m.coverUrl() != null ? m.coverUrl() : "",
                    "platform", m.platform()
            ));
        }
        result.put("recentPlays", recentPlays);
        return result;
    }

    @GetMapping("/users/{username}/likes")
    public Map<String, Object> userLikes(@PathVariable String username) {
        long count = likeRecordRepository.countByRequesterName(username);
        return Map.of("username", username, "likes", count);
    }

    @GetMapping("/users/{username}/playlists")
    public List<Map<String, Object>> userPlaylists(@PathVariable String username) {
        // 忽略大小写匹配公开主页用户名
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        return playlistRepository.findByUserIdAndIsPublic(user.getId(), true).stream()
                .map(this::buildPlaylistCard)
                .collect(Collectors.toList());
    }

    /** 公开主页：该用户获得的全部称号（含颜色），current 标记当前佩戴的称号 */
    @GetMapping("/users/{username}/titles")
    public Map<String, Object> userTitles(@PathVariable String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        String currentTitle = user.getCurrentTitle();
        List<Map<String, Object>> titles = titleRepository.findByUserIdOrderByGrantedAtAsc(user.getId()).stream()
                .map(t -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("title", t.getTitle());
                    item.put("color", resolveTitleColor(t.getTitle()));
                    item.put("current", t.getTitle().equals(currentTitle));
                    return item;
                })
                .toList();
        return Map.of("username", user.getUsername(), "titles", titles);
    }

    /** 按专辑名获取专辑歌曲列表（公开主页"专辑歌曲"展示与跳转）；返回 {id, name, songs} */
    @GetMapping("/album-songs/{platform}/{name}")
    public Mono<Map<String, Object>> albumSongs(@PathVariable String platform, @PathVariable String name,
                                                jakarta.servlet.http.HttpServletRequest request) {
        // M7：该接口未认证即可触发对第三方音乐 API 的多次外呼，按 IP 限流防打爆代理与第三方限额
        if (!ipRateLimiter.allow(request.getRemoteAddr(), 20, 60_000L)) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"));
        }
        if ("netease".equalsIgnoreCase(platform)) {
            return neteaseMusicProvider.getAlbumSongs(name);
        }
        if ("qq".equalsIgnoreCase(platform)) {
            return qqMusicProvider.getAlbumSongs(name);
        }
        if ("kugou".equalsIgnoreCase(platform)) {
            return kugouMusicProvider.getAlbumSongs(name);
        }
        return Mono.just(Map.of("id", "", "name", "", "songs", List.of()));
    }

    private JoinPermission resolvePermission(Channel channel) {
        return channel.getJoinPermission() != null ? channel.getJoinPermission() : JoinPermission.PUBLIC;
    }

    /** 查询称号定义的颜色（未定义时返回默认色） */
    private String resolveTitleColor(String title) {
        if (title == null || title.isBlank()) return "";
        return titleDefRepository.findByName(title).map(TitleDef::getColor).orElse("#ff5722");
    }

    private Map<String, Object> buildChannelCard(Channel channel) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", channel.getId());
        item.put("name", channel.getName());
        item.put("description", channel.getDescription() != null ? channel.getDescription() : "");
        item.put("onlineCount", channelSessionManager.getOnlineUserCount(channel.getId()));
        item.put("hasPassword", channel.getPasswordHash() != null && !channel.getPasswordHash().isEmpty());
        item.put("joinPermission", resolvePermission(channel).name());
        item.put("nowPlaying", nowPlayingSummary(channel.getId()));
        return item;
    }

    private Map<String, Object> nowPlayingSummary(Long channelId) {
        NowPlayingInfo info = musicPlayerService.getNowPlayingSummary(channelId);
        if (info == null || info.music() == null) {
            return null;
        }
        Map<String, Object> np = new HashMap<>();
        np.put("name", info.music().name());
        np.put("artists", info.music().artists());
        np.put("coverUrl", info.music().coverUrl() != null ? info.music().coverUrl() : "");
        return np;
    }

    private Map<String, Object> buildPlaylistCard(UserPlaylist playlist) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", playlist.getId());
        item.put("name", playlist.getName());
        item.put("category", playlist.getCategory() != null ? playlist.getCategory() : "");
        item.put("coverUrl", playlist.getCoverUrl() != null ? playlist.getCoverUrl() : "");
        item.put("songCount", playlistItemRepository.countByPlaylistId(playlist.getId()));
        item.put("isPublic", playlist.isPublic());
        return item;
    }
}
