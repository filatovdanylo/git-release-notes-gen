package me.automatedgitdiffnotesgenerator.dto;

import me.automatedgitdiffnotesgenerator.entity.User;

import java.time.OffsetDateTime;

public record UserResponse(
        String username,
        User.Role role,
        OffsetDateTime createdAt
) {
}
