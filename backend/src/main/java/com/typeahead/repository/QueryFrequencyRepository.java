package com.typeahead.repository;

import com.typeahead.model.QueryFrequency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface QueryFrequencyRepository extends JpaRepository<QueryFrequency, Long> {
    
    Optional<QueryFrequency> findByQuery(String query);

    List<QueryFrequency> findAllByOrderByCountDesc();

    @Modifying
    @Transactional
    @Query("UPDATE QueryFrequency q SET q.count = q.count + :delta, q.lastUpdated = CURRENT_TIMESTAMP WHERE q.query = :query")
    int incrementCount(@Param("query") String query, @Param("delta") long delta);
}
