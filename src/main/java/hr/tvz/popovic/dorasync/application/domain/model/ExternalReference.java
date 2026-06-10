package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

public record ExternalReference(String value) {

    private static final int MAX_SIZE = 255;

    public ExternalReference {
        requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_SIZE) {
            throw new IllegalArgumentException("value must not exceed " + MAX_SIZE + " characters");
        }
    }
}
