package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 称号定义（先制作、后下发）：名称 + 标签背景色。
 * 用户持有的称号（user_titles）引用这里的名称，展示为彩色矩形标签。
 */
@Entity
@Table(name = "title_defs", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TitleDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** 标签背景色（十六进制，如 #ff5722） */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String color = "#ff5722";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
