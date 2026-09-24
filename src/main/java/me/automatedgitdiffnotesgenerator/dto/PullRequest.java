package me.automatedgitdiffnotesgenerator.dto;

import java.util.Objects;

public record PullRequest(
        String number,
        String message
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PullRequest that = (PullRequest) o;
        return Objects.equals(number, that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(number);
    }
}
