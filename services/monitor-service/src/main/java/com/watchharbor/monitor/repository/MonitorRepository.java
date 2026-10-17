package com.watchharbor.monitor.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.watchharbor.monitor.entity.Monitor;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {
}
