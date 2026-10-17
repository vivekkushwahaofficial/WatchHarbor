package com.watchharbor.monitor.service;

public class InvalidMonitorUrlException extends RuntimeException {

    public InvalidMonitorUrlException(String message) {
        super(message);
    }
}
