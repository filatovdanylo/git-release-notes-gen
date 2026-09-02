package me.automatedgitdiffnotesgenerator.exception;

public class GitApiException extends RuntimeException {
    public GitApiException() {
        super();
    }
    public GitApiException(String message) {
        super(message);
    }
    public GitApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
