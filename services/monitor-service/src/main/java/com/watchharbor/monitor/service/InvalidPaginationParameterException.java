package com.watchharbor.monitor.service;

public class InvalidPaginationParameterException extends RuntimeException {

    public InvalidPaginationParameterException(String message) {
        super(message);
    }
}
