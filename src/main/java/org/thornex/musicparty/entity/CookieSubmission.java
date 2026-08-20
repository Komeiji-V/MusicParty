package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 用户提交的音源 Cookie 申请，由总管理员审核：
 * 通过 → 汇入 Cookie 池并授予「音源提供者」称号。
 */
@Entity
@Table(name = "cookie_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CookieSubmission {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 20)
    private String platform;

    // 允许 NULL：审核完成（通过/驳回）后凭证置空，仅保留审计行（谁/何时/哪个平台）
    @Column(columnDefinition = "TEXT")
    private String cookie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private Long reviewedBy;

    @Column
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
