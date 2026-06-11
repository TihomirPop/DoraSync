package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

/**
 * Explicit optionality for domain models: a value is either {@link Some} (guaranteed present and
 * non-null) or {@link None}. Callers must pattern-match to read it, so a domain model can never hand
 * back a {@code null} and never throw a {@link NullPointerException} on access.
 */
public sealed interface Maybe<T> {

    record Some<T>(T value) implements Maybe<T> {

        public Some {
            requireNonNull(value, "value must not be null");
        }
    }

    record None<T>() implements Maybe<T> {
    }

    static <T> Maybe<T> of(T value) {
        return value == null ? new None<>() : new Some<>(value);
    }
}
