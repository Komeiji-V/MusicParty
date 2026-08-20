package org.thornex.musicparty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.thornex.musicparty.dto.*;
import org.thornex.musicparty.entity.PlaylistItem;
import org.thornex.musicparty.entity.UserPlaylist;
import org.thornex.musicparty.repository.PlaylistItemRepository;
import org.thornex.musicparty.repository.UserPlaylistRepository;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserPlaylistService {

    private final UserPlaylistRepository playlistRepository;
    private final PlaylistItemRepository itemRepository;
    private final ObjectMapper objectMapper;


    @Transactional
    public UserPlaylistDto create(Long userId, CreatePlaylistRequest req) {
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "歌单名称不能为空");
        }
        UserPlaylist playlist = UserPlaylist.builder()
                .userId(userId)
                .name(name)
                .category(trimToNull(req.category()))
                .coverUrl(trimToNull(req.coverUrl()))
                .isPublic(req.isPublic() != null && req.isPublic())
                .build();
        playlist = playlistRepository.save(playlist);
        log.info("创建歌单: {} (ID: {}), 用户: {}", playlist.getName(), playlist.getId(), userId);
        return toDto(playlist, 0);
    }

    /** 使用图片 URL 作为歌单封面 */
    @Transactional
    public String updateCoverUrl(Long userId, Long id, String url) {
        UserPlaylist playlist = getOwned(userId, id);
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面 URL 不能为空");
        }
        playlist.setCoverUrl(url.trim());
        playlistRepository.save(playlist);
        log.info("歌单封面 URL 已更新: playlist {}, url {}", id, url);
        return playlist.getCoverUrl();
    }

    public List<UserPlaylistDto> list(Long userId) {
        return playlistRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(pl -> toDto(pl, itemRepository.countByPlaylistId(pl.getId())))
                .toList();
    }

    public List<String> categories(Long userId) {
        return playlistRepository.findDistinctCategoriesByUserId(userId);
    }

    @Transactional
    public UserPlaylistDto update(Long userId, Long id, UpdatePlaylistRequest req) {
        UserPlaylist playlist = getOwned(userId, id);
        if (req.name() != null && !req.name().trim().isEmpty()) {
            playlist.setName(req.name().trim());
        }
        if (req.category() != null) {
            playlist.setCategory(trimToNull(req.category()));
        }
        if (req.isPublic() != null) {
            playlist.setPublic(req.isPublic());
        }
        playlist = playlistRepository.save(playlist);
        return toDto(playlist, itemRepository.countByPlaylistId(playlist.getId()));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserPlaylist playlist = getOwned(userId, id);
        itemRepository.deleteByPlaylistId(playlist.getId());
        playlistRepository.delete(playlist);
        log.info("删除歌单: {} (ID: {}), 用户: {}", playlist.getName(), id, userId);
    }

    public List<PlaylistItemDto> getItems(Long userId, Long playlistId) {
        getOwned(userId, playlistId);
        return itemRepository.findByPlaylistIdOrderByPosition(playlistId).stream()
                .map(item -> new PlaylistItemDto(item.getId(), parseSongData(item)))
                .toList();
    }

    @Transactional
    public Music addItem(Long userId, Long playlistId, Music music) {
        getOwned(userId, playlistId);
        if (music == null || music.id() == null || music.id().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "歌曲数据不完整");
        }
        if (itemRepository.existsByPlaylistIdAndMusicId(playlistId, music.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "歌曲已在歌单中");
        }
        PlaylistItem item = PlaylistItem.builder()
                .playlistId(playlistId)
                .musicId(music.id())
                .platform(music.platform() != null ? music.platform() : "")
                .songData(writeSongData(music))
                .position((int) itemRepository.countByPlaylistId(playlistId))
                .build();
        itemRepository.save(item);
        return music;
    }

    @Transactional
    public int importSongs(Long userId, Long playlistId, List<Music> songs) {
        getOwned(userId, playlistId);
        if (songs == null || songs.isEmpty()) {
            return 0;
        }
        int position = (int) itemRepository.countByPlaylistId(playlistId);
        int added = 0;
        for (Music music : songs) {
            if (music == null || music.id() == null || music.id().isEmpty()) continue;
            if (itemRepository.existsByPlaylistIdAndMusicId(playlistId, music.id())) continue;
            itemRepository.save(PlaylistItem.builder()
                    .playlistId(playlistId)
                    .musicId(music.id())
                    .platform(music.platform() != null ? music.platform() : "")
                    .songData(writeSongData(music))
                    .position(position++)
                    .build());
            added++;
        }
        return added;
    }

    @Transactional
    public void removeItem(Long userId, Long playlistId, Long itemId) {
        getOwned(userId, playlistId);
        long removed = itemRepository.deleteByIdAndPlaylistId(itemId, playlistId);
        if (removed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "歌曲不存在");
        }
    }

    public String exportTxt(Long userId, Long playlistId) {
        getOwned(userId, playlistId);
        List<PlaylistItemDto> items = getItems(userId, playlistId);
        StringBuilder sb = new StringBuilder();
        for (PlaylistItemDto item : items) {
            Music music = item.music();
            String artists = music.artists() == null || music.artists().isEmpty()
                    ? "未知歌手" : String.join(" / ", music.artists());
            sb.append(music.name()).append(" - ").append(artists).append("\n");
        }
        return sb.toString();
    }

    public PlaylistExportJson exportJson(Long userId, Long playlistId) {
        UserPlaylist playlist = getOwned(userId, playlistId);
        List<Music> songs = getItems(userId, playlistId).stream()
                .map(PlaylistItemDto::music)
                .toList();
        return new PlaylistExportJson(playlist.getName(), playlist.getCategory(), songs);
    }

    public String getPlaylistName(Long userId, Long playlistId) {
        return getOwned(userId, playlistId).getName();
    }

    private UserPlaylist getOwned(Long userId, Long playlistId) {
        return playlistRepository.findByIdAndUserId(playlistId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "歌单不存在"));
    }

    private UserPlaylistDto toDto(UserPlaylist pl, long itemCount) {
        return new UserPlaylistDto(pl.getId(), pl.getName(), pl.getCategory(), pl.getCoverUrl(),
                pl.isPublic(), itemCount, pl.getCreatedAt(), pl.getUpdatedAt());
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String writeSongData(Music music) {
        try {
            return objectMapper.writeValueAsString(music);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "歌曲数据序列化失败");
        }
    }

    private Music parseSongData(PlaylistItem item) {
        try {
            return objectMapper.readValue(item.getSongData(), Music.class);
        } catch (JsonProcessingException e) {
            log.warn("解析歌单歌曲失败: item={}, error={}", item.getId(), e.getMessage());
            return new Music(item.getMusicId(), item.getMusicId(), List.of(), 0L, item.getPlatform(), "");
        }
    }
}
