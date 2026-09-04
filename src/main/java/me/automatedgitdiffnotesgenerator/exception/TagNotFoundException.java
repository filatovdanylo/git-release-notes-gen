package me.automatedgitdiffnotesgenerator.exception;

public class TagNotFoundException extends RuntimeException {
    public TagNotFoundException() {
        super();
    }
    public TagNotFoundException(String message) {
        super(message);
    }
    public TagNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
