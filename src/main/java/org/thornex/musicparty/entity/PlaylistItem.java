package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "playlist_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaylistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    @Column(name = "music_id", nullable = false, length = 100)
    private String musicId;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(name = "song_data", columnDefinition = "TEXT")
    private String songData;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }
}
