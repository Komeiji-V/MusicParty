package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.LikeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LikeRecordRepository extends JpaRepository<LikeRecord, Long> {
    long countByChannelIdAndMusicIdAndPlatform(Long channelId, String musicId, String platform);

    long countByRequesterName(String requesterName);
}
