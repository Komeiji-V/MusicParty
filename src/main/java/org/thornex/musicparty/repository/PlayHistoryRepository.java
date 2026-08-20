package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.PlayHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PlayHistoryRepository extends JpaRepository<PlayHistory, Long> {
    List<PlayHistory> findByChannelIdOrderByPlayedAtDesc(Long channelId);

    List<PlayHistory> findByChannelId(Long channelId);

    @Transactional
    void deleteByChannelId(Long channelId);
}
