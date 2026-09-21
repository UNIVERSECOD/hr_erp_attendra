package com.hic.exception;

public class DeviceSyncException extends RuntimeException {
    public DeviceSyncException(String message) {
        super(message);
    }

    public DeviceSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
