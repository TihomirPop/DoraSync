package hr.tvz.popovic.dorasync.application.domain.model;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public record Id(UUID value) {

    public Id {
        requireNonNull(value, "value must not be null");
    }
}
