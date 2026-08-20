package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.MusicCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MusicCacheRepository extends JpaRepository<MusicCache, Long> {
    Optional<MusicCache> findByChannelIdAndPlatformAndMusicId(Long channelId, String platform, String musicId);
}
