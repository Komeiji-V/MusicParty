package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.PlaylistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Long> {

    List<PlaylistItem> findByPlaylistIdOrderByPosition(Long playlistId);

    long deleteByPlaylistId(Long playlistId);

    long countByPlaylistId(Long playlistId);

    boolean existsByPlaylistIdAndMusicId(Long playlistId, String musicId);

    long deleteByIdAndPlaylistId(Long id, Long playlistId);
}
