package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

public record DeploykoService(String value) {

    public DeploykoService {
        requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static DeploykoService of(ExternalReference externalReference) {
        return new DeploykoService(externalReference.value());
    }
}
