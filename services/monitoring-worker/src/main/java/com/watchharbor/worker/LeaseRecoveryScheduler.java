package com.watchharbor.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LeaseRecoveryScheduler {

    private static final Logger log
            = LoggerFactory.getLogger(LeaseRecoveryScheduler.class);

    private final JobClaimService jobClaimService;

    public LeaseRecoveryScheduler(JobClaimService jobClaimService) {
        this.jobClaimService = jobClaimService;
    }

    @Scheduled(
            fixedDelayString
            = "${worker.lease-recovery-interval-ms:10000}"
    )
    public void recoverExpiredLeases() {
        int recoveredCount = jobClaimService.recoverExpiredLeases();

        if (recoveredCount > 0) {
            log.info(
                    "Recovered {} expired monitoring job lease(s)",
                    recoveredCount
            );
        }
    }
}
