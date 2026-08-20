package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.ChannelAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelAdminRepository extends JpaRepository<ChannelAdmin, Long> {
    List<ChannelAdmin> findByChannelId(Long channelId);

    Optional<ChannelAdmin> findByUserIdAndChannelId(Long userId, Long channelId);

    @Transactional
    void deleteByChannelIdAndUserId(Long channelId, Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
