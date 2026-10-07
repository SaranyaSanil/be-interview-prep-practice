package com.interviewprep.shorturl;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * Increments in a single UPDATE statement. The database locks the row for the update, so concurrent visits
     * cannot overwrite each other (no read-modify-write lost updates).
     */
    @Modifying
    @Query("update ShortUrl s set s.visitCount = s.visitCount + 1 where s.id = :id")
    int incrementVisitCount(@Param("id") Long id);
}
