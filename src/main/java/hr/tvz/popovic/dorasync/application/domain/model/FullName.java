package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

public record FullName(String value) {

    public FullName {
        requireNonNull(value, "value must not be null");
        int slash = value.indexOf('/');
        if (slash <= 0 || slash != value.lastIndexOf('/') || slash == value.length() - 1) {
            throw new IllegalArgumentException("value must be in 'owner/name' format: " + value);
        }
    }

    public static FullName of(ExternalReference externalReference) {
        return new FullName(externalReference.value());
    }

    public String owner() {
        return value.substring(0, value.indexOf('/'));
    }

    public String name() {
        return value.substring(value.indexOf('/') + 1);
    }
}
