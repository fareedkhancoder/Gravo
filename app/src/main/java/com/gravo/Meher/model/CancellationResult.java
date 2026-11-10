package com.gravo.Meher.model;

public class CancellationResult {
    private final boolean success;
    private final String message;

    public CancellationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
