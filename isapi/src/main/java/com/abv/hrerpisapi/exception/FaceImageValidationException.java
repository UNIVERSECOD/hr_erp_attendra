package com.abv.hrerpisapi.exception;

public class FaceImageValidationException extends RuntimeException {

    public FaceImageValidationException(String message) {
        super(message);
    }

    public FaceImageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
