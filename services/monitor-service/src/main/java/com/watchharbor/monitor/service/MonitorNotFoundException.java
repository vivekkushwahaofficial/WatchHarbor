package com.watchharbor.monitor.service;

public class MonitorNotFoundException extends RuntimeException {

    public MonitorNotFoundException(String message) {
        super(message);
    }
}
