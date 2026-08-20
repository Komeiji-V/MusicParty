package org.thornex.musicparty.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thornex.musicparty.entity.TitleDef;

import java.util.List;
import java.util.Optional;

public interface TitleDefRepository extends JpaRepository<TitleDef, Long> {
    Optional<TitleDef> findByName(String name);
    List<TitleDef> findAllByOrderByCreatedAtAsc();
}
