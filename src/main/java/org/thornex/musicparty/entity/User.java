package org.thornex.musicparty.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true)
    private Long authUid;

    @Column
    private String passwordHash;

    @Column(length = 100)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(length = 10)
    private String verificationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(length = 100)
    private String avatarUrl;

    /** 当前展示称号（娱乐标签，最多显示一个） */
    @Column(name = "current_title", length = 50)
    private String currentTitle;

    /** 个人主页公开展示：最喜欢的歌曲（JSON：name/artists/coverUrl/musicId/platform/album） */
    @Column(name = "featured_song", columnDefinition = "TEXT")
    private String featuredSong;

    /** 个人主页公开展示：最喜欢的专辑（JSON：name/coverUrl/album） */
    @Column(name = "featured_album", columnDefinition = "TEXT")
    private String featuredAlbum;

    /** 个人主页公开展示：最喜欢的一段歌词 */
    @Column(name = "favorite_lyric", columnDefinition = "TEXT")
    private String favoriteLyric;

    /** 最喜欢的一段歌词对应的歌曲名 */
    @Column(name = "favorite_lyric_song", columnDefinition = "TEXT")
    private String favoriteLyricSong;

    /** 个人主页公开展示布局模板：classic / album-big / song-big / both-big */
    @Column(name = "featured_layout", length = 32)
    private String featuredLayout;

    /** 个人主页公开展示主题：light / dark / retro / gallery */
    @Column(name = "featured_theme", length = 32)
    private String featuredTheme;

    /** 个人主页公开展示版式模板：hero / magazine / minimal / wall */
    @Column(name = "featured_template", length = 32)
    private String featuredTemplate;

    /** 个人主页公开展示：部件桌面布局（JSON：[{type,x,y,w,h}]，网格 4 列） */
    @Column(name = "featured_widgets", columnDefinition = "TEXT")
    private String featuredWidgets;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 身份阶层：总管理员（认证中心 admin 角色）与普通成员。
     * 权限不再人工分配，直接读取认证中心 role==admin 判定；游客不落库（未登录状态）。
     */
    public enum UserRole {
        SUPER_ADMIN, USER
    }
}
