package com.watchharbor.worker.client;

public class MonitorServiceException extends RuntimeException {

    public MonitorServiceException(String message) {
        super(message);
    }

    public MonitorServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
