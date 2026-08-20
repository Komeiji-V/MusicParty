package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "music_queue")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MusicQueueItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "user_token")
    private String userToken;

    @Column(name = "enqueuer_name")
    private String enqueuerName;

    @Column(name = "enqueuer_guest")
    @Builder.Default
    private Boolean enqueuerGuest = false;

    @Column(name = "song_data", nullable = false, columnDefinition = "TEXT")
    private String songData;

    @Column(name = "music_id", length = 100)
    private String musicId;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String priority = "REGULAR";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "READY";

    @Column
    @Builder.Default
    private Integer position = 0;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}
