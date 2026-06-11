package hr.tvz.popovic.dorasync.application.domain.model;

public sealed interface BuildCursor {

    record After(long buildNumber) implements BuildCursor {
    }

    record Beginning() implements BuildCursor {
    }
}
