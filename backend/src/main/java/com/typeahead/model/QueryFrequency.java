package com.typeahead.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "query_frequency")
public class QueryFrequency {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_text", unique = true, nullable = false)
    private String query;

    @Column(name = "search_count", nullable = false)
    private long count;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    public QueryFrequency() {}

    public QueryFrequency(String query, long count) {
        this.query = query;
        this.count = count;
    }

    public QueryFrequency(Long id, String query, long count, LocalDateTime lastUpdated) {
        this.id = id;
        this.query = query;
        this.count = count;
        this.lastUpdated = lastUpdated;
    }

    @PrePersist
    protected void onCreate() {
        lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
