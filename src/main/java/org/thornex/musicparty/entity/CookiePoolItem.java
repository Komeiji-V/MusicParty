package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 音源 Cookie 池：每个平台（netease/qq/kugou/bilibili）多个 Cookie，
 * 请求失败自动轮换到下一个。
 */
@Entity
@Table(name = "cookie_pool", indexes = @Index(name = "idx_cookie_pool_platform", columnList = "platform"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CookiePoolItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cookie;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** 连续失败次数（达到阈值自动禁用，由服务层维护） */
    @Column(nullable = false)
    @Builder.Default
    private int failCount = 0;

    /** 自动调用失败的错误标记（供管理员审查） */
    @Column(name = "error_mark", nullable = false)
    @Builder.Default
    private boolean errorMark = false;

    /** 最近一次失败原因 */
    @Column(name = "error_reason", columnDefinition = "TEXT")
    private String errorReason;

    /** 最近一次失败时间 */
    @Column(name = "last_error_at")
    private LocalDateTime lastErrorAt;

    /** VIP 检测结果：-1 未检测，0 非 VIP，>0 会员等级 */
    @Column(name = "vip_type", nullable = false)
    @Builder.Default
    private int vipType = -1;

    /** VIP 检测时间 */
    @Column(name = "vip_checked_at")
    private LocalDateTime vipCheckedAt;

    /** 提交者（用户提交并审核通过的 Cookie 记录来源用户） */
    @Column
    private Long addedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
