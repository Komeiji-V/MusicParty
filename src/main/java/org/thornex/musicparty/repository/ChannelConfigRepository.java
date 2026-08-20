package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.ChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelConfigRepository extends JpaRepository<ChannelConfig, Long> {
    Optional<ChannelConfig> findByChannelIdAndConfigKey(Long channelId, String configKey);

    List<ChannelConfig> findByChannelId(Long channelId);
}
