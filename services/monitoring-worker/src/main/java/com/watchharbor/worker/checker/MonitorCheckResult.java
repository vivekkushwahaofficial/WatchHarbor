package com.watchharbor.worker.checker;

import java.time.Duration;

public record MonitorCheckResult(
        Outcome outcome,
        Integer httpStatus,
        Duration responseTime,
        String errorCode) {

    public enum Outcome {
        SUCCESS,
        HTTP_ERROR,
        TIMEOUT,
        NETWORK_ERROR,
        REJECTED
    }

    public MonitorCheckResult {
        if (outcome == null) {
            throw new IllegalArgumentException(
                    "Outcome must not be null");
        }

        if (responseTime == null || responseTime.isNegative()) {
            throw new IllegalArgumentException(
                    "Response time must not be negative or null");
        }

        if (httpStatus != null
                && (httpStatus < 100 || httpStatus > 599)) {
            throw new IllegalArgumentException(
                    "Invalid HTTP status");
        }
    }

    public static MonitorCheckResult success(
            int status, Duration responseTime) {
        return new MonitorCheckResult(
                Outcome.SUCCESS, status, responseTime, null);
    }

    public static MonitorCheckResult httpError(
            int status, Duration responseTime) {
        return new MonitorCheckResult(
                Outcome.HTTP_ERROR, status, responseTime, "HTTP_ERROR");
    }

    public static MonitorCheckResult failure(
            Outcome outcome,
            Duration responseTime,
            String errorCode) {

        if (outcome == Outcome.SUCCESS
                || outcome == Outcome.HTTP_ERROR) {
            throw new IllegalArgumentException(
                    "Use success() or httpError() for HTTP outcomes");
        }

        return new MonitorCheckResult(
                outcome, null, responseTime, errorCode);
    }
}
