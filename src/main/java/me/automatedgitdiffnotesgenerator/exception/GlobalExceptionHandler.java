package me.automatedgitdiffnotesgenerator.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GitApiException.class)
    public ProblemDetail handleGitApiException(GitApiException ex) {
        HttpStatus status = ex.getStatus() != null ? ex.getStatus() : HttpStatus.BAD_GATEWAY;

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle("GitHub API Error");
        problemDetail.setType(URI.create("https://api.github.com/errors/git-api-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        log.error("GitHub API error occurred: {}", ex.getMessage(), ex);

        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed for one or more request parameters"
        );
        problemDetail.setTitle("Invalid Request Body");

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        problemDetail.setProperty("errors", fieldErrors);
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Validation failed for request parameters: {}", fieldErrors);

        return problemDetail;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflictException(ConflictException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        problemDetail.setTitle("Conflict");
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Conflict: {}", ex.getMessage());

        return problemDetail;
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ProblemDetail handleUsernameNotFoundException(UsernameNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid username or password"
        );

        problemDetail.setTitle("Authorization error");
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Username or password is invalid: {}", ex.getMessage());

        return problemDetail;
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleIllegalStateException(ForbiddenException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.getMessage()
        );

        problemDetail.setTitle("Forbidden");
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Forbidden: {}", ex.getMessage());

        return problemDetail;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Unexpected authentication error"
        );

        problemDetail.setTitle("Authentication failed");
        problemDetail.setProperty("timestamp", Instant.now());

        log.error("Authentication failed: {}", ex.getMessage());

        return problemDetail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Uniqueness constraint violated"
        );

        problemDetail.setTitle("Data Integrity Violation");
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Data Integrity Violation: {}", ex.getMessage());

        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected system error occurred."
        );
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());

        log.error("An unexpected system error occurred.", e);

        return problemDetail;
    }
}
