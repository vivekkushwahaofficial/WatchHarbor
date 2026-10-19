package com.watchharbor.worker;

public class StaleClaimException extends RuntimeException {

    public StaleClaimException() {
        super("The job claim expired or was replaced before completion");
    }
}
