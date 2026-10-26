package com.example.minijarvis.ai;

/** The requested provider is unknown or not configured. Mapped to HTTP 400. */
public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) {
        super(message);
    }
}
