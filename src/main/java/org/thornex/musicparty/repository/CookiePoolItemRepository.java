package org.thornex.musicparty.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thornex.musicparty.entity.CookiePoolItem;

import java.util.List;
import java.util.Optional;

public interface CookiePoolItemRepository extends JpaRepository<CookiePoolItem, Long> {
    List<CookiePoolItem> findByPlatformOrderByIdAsc(String platform);
    List<CookiePoolItem> findByPlatformAndEnabledTrueOrderByIdAsc(String platform);
    Optional<CookiePoolItem> findByPlatformAndCookie(String platform, String cookie);
    /** 某用户提交的池内 Cookie 数量（用于删除后判断是否回收称号） */
    long countByAddedBy(Long addedBy);
}
