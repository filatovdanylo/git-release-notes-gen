package me.automatedgitdiffnotesgenerator.dto;

public record TokenResponse(
        String token,
        String header,
        long expirationTimeMs
) {
}
