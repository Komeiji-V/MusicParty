package org.thornex.musicparty.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thornex.musicparty.entity.UserTitle;

import java.util.List;

public interface UserTitleRepository extends JpaRepository<UserTitle, Long> {
    List<UserTitle> findByUserIdOrderByGrantedAtAsc(Long userId);
    boolean existsByUserIdAndTitle(Long userId, String title);
}
