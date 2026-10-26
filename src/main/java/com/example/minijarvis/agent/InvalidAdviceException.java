package com.example.minijarvis.agent;

/** The model's output was not valid JSON or did not match the advice schema. */
public class InvalidAdviceException extends RuntimeException {
    public InvalidAdviceException(String message) {
        super(message);
    }

    public InvalidAdviceException(String message, Throwable cause) {
        super(message, cause);
    }
}
