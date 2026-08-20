package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.MusicQueueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MusicQueueItemRepository extends JpaRepository<MusicQueueItem, Long> {
    List<MusicQueueItem> findByChannelIdOrderByPosition(Long channelId);

    List<MusicQueueItem> findByChannelIdAndStatus(Long channelId, String status);

    @Transactional
    void deleteByChannelId(Long channelId);
}
