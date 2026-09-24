package me.automatedgitdiffnotesgenerator.dto;

import jakarta.validation.constraints.NotNull;

public record PRInfo(
        String title,
        String url,
        String user
) {
    @Override
    public String toString() {
        return title + " Author: " + user + " with url " + url;
    }
}
