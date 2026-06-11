package hr.tvz.popovic.dorasync.application.domain.model;

import java.util.Locale;

public enum DeploymentStatus {

    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    CANCELED,
    UNKNOWN;

    public static DeploymentStatus from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "IN_PROGRESS" -> IN_PROGRESS;
            case "SUCCESS" -> SUCCESS;
            case "FAILURE", "FAILED" -> FAILURE;
            case "CANCELED", "CANCELLED" -> CANCELED;
            default -> UNKNOWN;
        };
    }

    /**
     * A deployment attempt has settled: it will not change status again. Only terminal attempts are
     * persisted, so {@code IN_PROGRESS} (still in flight) and {@code UNKNOWN} (unrecognized) are not.
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILURE || this == CANCELED;
    }
}
