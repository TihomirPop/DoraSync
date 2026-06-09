package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

public record Service(Id id, ServiceName name) {

    public Service {
        requireNonNull(id, "id must not be null");
        requireNonNull(name, "name must not be null");
    }
}

