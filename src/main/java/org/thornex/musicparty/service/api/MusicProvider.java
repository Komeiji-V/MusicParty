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

    Mono<List<UserSearchResult>> searchUsers(String keyword);

    Mono<Music> getPlayableMusic(String musicId);

    boolean isEnabled();

    void setCookie(String cookie);

    String getCookie();
}
