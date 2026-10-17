package com.watchharbor.monitor.repository;

import java.sql.Connection;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.watchharbor.monitor.entity.Monitor;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MonitorRepositoryIntegrationTest {

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    void savesRetrievesAndDeletesMonitorUsingPostgreSql() throws Exception {
        // Safety check: never run this test against the hosted database.
        try (Connection connection = dataSource.getConnection()) {
            String databaseUrl = connection.getMetaData().getURL();

            assertThat(databaseUrl)
                    .contains("/watchharbor_test");
        }

        assertThat(environment.getActiveProfiles())
                .contains("test");

        // Save a monitor using the real JPA repository.
        Monitor monitor = new Monitor(
                "Integration Test Monitor",
                "https://example.com"
        );

        Monitor savedMonitor = monitorRepository.saveAndFlush(monitor);

        assertThat(savedMonitor.getId()).isNotNull();
        assertThat(savedMonitor.getCreatedAt()).isNotNull();

        // Retrieve the record from PostgreSQL.
        Monitor retrievedMonitor = monitorRepository
                .findById(savedMonitor.getId())
                .orElseThrow();

        assertThat(retrievedMonitor.getName())
                .isEqualTo("Integration Test Monitor");

        assertThat(retrievedMonitor.getUrl())
                .isEqualTo("https://example.com");

        // Delete the record and flush the SQL operation.
        monitorRepository.delete(retrievedMonitor);
        monitorRepository.flush();

        assertThat(monitorRepository.findById(savedMonitor.getId()))
                .isEmpty();
    }
}
