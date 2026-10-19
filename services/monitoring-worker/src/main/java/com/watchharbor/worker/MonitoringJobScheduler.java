package com.watchharbor.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitoringJobScheduler {

    private static final Logger log
            = LoggerFactory.getLogger(MonitoringJobScheduler.class);

    private final MonitoringJobProcessor jobProcessor;

    public MonitoringJobScheduler(MonitoringJobProcessor jobProcessor) {
        this.jobProcessor = jobProcessor;
    }

    @Scheduled(
            fixedDelayString
            = "${worker.polling-interval-ms:1000}"
    )
    public void processNextJob() {
        try {
            jobProcessor.processNextJob();
        } catch (RuntimeException exception) {
            log.error(
                    "Unexpected error while processing a monitoring job",
                    exception
            );
        }
    }
}
