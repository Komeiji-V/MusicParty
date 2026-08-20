package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "like_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LikeRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "music_id", nullable = false, length = 100)
    private String musicId;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(name = "music_name", nullable = false)
    private String musicName;

    @Column(columnDefinition = "TEXT")
    private String artists;

    @Column(name = "requester_name")
    private String requesterName;

    @Column(name = "liker_username")
    private String likerUsername;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
