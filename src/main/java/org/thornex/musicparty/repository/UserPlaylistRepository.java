package org.thornex.musicparty.repository;

import org.thornex.musicparty.entity.UserPlaylist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPlaylistRepository extends JpaRepository<UserPlaylist, Long> {

    List<UserPlaylist> findByUserIdOrderBySortOrderAscCreatedAtDesc(Long userId);

    Optional<UserPlaylist> findByIdAndUserId(Long id, Long userId);

    List<UserPlaylist> findByUserIdAndIsPublic(Long userId, boolean isPublic);

    @Query("SELECT DISTINCT p.category FROM UserPlaylist p WHERE p.userId = :userId AND p.category IS NOT NULL AND p.category <> '' ORDER BY p.category")
    List<String> findDistinctCategoriesByUserId(@Param("userId") Long userId);
}
