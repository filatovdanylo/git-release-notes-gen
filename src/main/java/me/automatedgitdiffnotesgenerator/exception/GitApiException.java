package me.automatedgitdiffnotesgenerator.exception;

import org.springframework.http.HttpStatus;

public class GitApiException extends RuntimeException {
    private final HttpStatus status;

    public GitApiException(String message) {
        super(message);
        this.status = HttpStatus.BAD_GATEWAY;
    }

    public GitApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public GitApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.BAD_GATEWAY;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
