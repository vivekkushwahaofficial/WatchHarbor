package com.watchharbor.monitor.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitors")
public class Monitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(
            name = "check_interval_seconds",
            nullable = false
    )
    private int checkIntervalSeconds = 300;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Monitor() {
        // Required by JPA.
    }

    public Monitor(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public void updateDetails(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public void updateScheduling(
            Integer checkIntervalSeconds,
            Boolean enabled
    ) {
        if (checkIntervalSeconds != null) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }

        if (enabled != null) {
            this.enabled = enabled;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public int getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
