package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

public record GitIdentity(Maybe<String> name, Maybe<String> email) {

    public GitIdentity {
        requireNonNull(name, "name must not be null");
        requireNonNull(email, "email must not be null");
    }
}
