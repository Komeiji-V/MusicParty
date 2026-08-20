package org.thornex.musicparty.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thornex.musicparty.entity.CookieSubmission;

import java.util.List;

public interface CookieSubmissionRepository extends JpaRepository<CookieSubmission, Long> {
    List<CookieSubmission> findByStatusOrderByCreatedAtAsc(CookieSubmission.Status status);
    boolean existsByUserIdAndPlatformAndStatus(Long userId, String platform, CookieSubmission.Status status);
}
