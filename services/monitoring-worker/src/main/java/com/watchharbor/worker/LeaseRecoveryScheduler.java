package com.watchharbor.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LeaseRecoveryScheduler {

    private static final Logger log
            = LoggerFactory.getLogger(LeaseRecoveryScheduler.class);

    private final JobClaimService jobClaimService;
    private final int maxAttempts;

    public LeaseRecoveryScheduler(
            JobClaimService jobClaimService,
            @Value("${worker.max-attempts:3}") int maxAttempts
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be at least one");
        }

        this.jobClaimService = jobClaimService;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(
            fixedDelayString
            = "${worker.lease-recovery-interval-ms:10000}"
    )
    public void recoverExpiredLeases() {
        int processedCount
                = jobClaimService.recoverExpiredLeases(maxAttempts);

        if (processedCount > 0) {
            log.info(
                    "Processed {} expired monitoring job lease(s)",
                    processedCount
            );
        }
    }
}
