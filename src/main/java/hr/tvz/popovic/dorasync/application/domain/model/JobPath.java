package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

public record JobPath(String value) {

    public JobPath {
        requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static JobPath of(ExternalReference externalReference) {
        return new JobPath(externalReference.value());
    }
}
