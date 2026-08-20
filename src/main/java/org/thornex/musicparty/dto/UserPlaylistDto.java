package org.thornex.musicparty.dto;

import java.time.LocalDateTime;

public record UserPlaylistDto(
        Long id,
        String name,
        String category,
        String coverUrl,
        boolean isPublic,
        long itemCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
