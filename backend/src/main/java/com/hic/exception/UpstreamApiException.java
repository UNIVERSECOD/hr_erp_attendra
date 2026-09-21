package com.hic.exception;

import org.springframework.http.HttpStatusCode;

public class UpstreamApiException extends RuntimeException {
    private final HttpStatusCode statusCode;

    public UpstreamApiException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
