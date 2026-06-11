package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

public record ImageVersion(String value) {

    public ImageVersion {
        requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
