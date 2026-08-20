package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChannelIdOrderByCreatedAtDesc(Long channelId);

    List<ChatMessage> findByChannelIdAndCreatedAtBefore(Long channelId, LocalDateTime createdAt);

    @Transactional
    void deleteByChannelId(Long channelId);
}
