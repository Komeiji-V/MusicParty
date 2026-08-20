package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 用户称号（娱乐标签）：一个用户可拥有多个，但展示时最多选一个。
 * 「音源提供者」由 Cookie 审核通过自动下发。
 */
@Entity
@Table(name = "user_titles", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "title"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String title;

    /** 称号来源说明（如：cookie 审核通过） */
    @Column(length = 100)
    private String source;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    @PrePersist
    protected void onCreate() {
        grantedAt = LocalDateTime.now();
    }
}
