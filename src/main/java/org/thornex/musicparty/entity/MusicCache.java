package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "music_cache", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"channel_id", "platform", "music_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MusicCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "music_id", nullable = false)
    private String musicId;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    @Builder.Default
    private Long fileSize = 0L;

    @Column(name = "cached_at")
    private LocalDateTime cachedAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @PrePersist
    protected void onCreate() {
        cachedAt = LocalDateTime.now();
        lastAccessedAt = LocalDateTime.now();
    }
}
