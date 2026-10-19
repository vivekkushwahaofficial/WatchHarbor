package com.watchharbor.worker.client;

public class MonitorNotFoundException extends RuntimeException {

    public MonitorNotFoundException(Long monitorId) {
        super("Monitor not found: " + monitorId);
    }
}
