package org.thornex.musicparty.service.api;

import org.thornex.musicparty.dto.*;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MusicProvider {
    String getPlatformName();

    Mono<List<Music>> searchMusic(String keyword, int limit, int offset);

    Mono<String> getPlayUrl(String musicId, String quality);

    /** 按频道取播放地址（频道可手动指定优先使用的 Cookie）；默认回退到无频道版本 */
    default Mono<String> getPlayUrl(String musicId, String quality, Long channelId) {
        return getPlayUrl(musicId, quality);
    }

    Mono<Music> getSongDetail(String musicId);

    Mono<List<Music>> getPlaylistSongs(String playlistId, int offset, int limit);

    Mono<List<Playlist>> getUserPlaylists(String userId);

    Mono<String> getLyric(String musicId);

    /**
     * 结构化歌词（含翻译/罗马音，供歌词面板切换显示）。
     * 返回 { lrc, tlyric, romalrc }（LRC 格式字符串，无则空串）。
     * 默认实现只返回原文；支持翻译/罗马音的平台（网易云）自行覆盖。
     */
    default Mono<java.util.Map<String, String>> getLyricFull(String musicId) {
        return getLyric(musicId).map(lrc -> {
            String s = lrc == null ? "" : lrc;
            return java.util.Map.of("lrc", s, "tlyric", "", "romalrc", "");
        });
    }

    Mono<List<UserSearchResult>> searchUsers(String keyword);

    Mono<Music> getPlayableMusic(String musicId);

    boolean isEnabled();

    void setCookie(String cookie);

    String getCookie();
}
