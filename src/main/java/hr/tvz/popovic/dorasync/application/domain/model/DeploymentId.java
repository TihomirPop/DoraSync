package hr.tvz.popovic.dorasync.application.domain.model;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public record DeploymentId(UUID value) {

    public DeploymentId {
        requireNonNull(value, "value must not be null");
    }
}
