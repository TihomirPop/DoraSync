package hr.tvz.popovic.dorasync.application.domain.model;

import java.util.Locale;

public enum BuildResult {

    SUCCESS,
    FAILURE,
    UNSTABLE,
    ABORTED,
    NOT_BUILT,
    UNKNOWN;

    public static BuildResult from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SUCCESS" -> SUCCESS;
            case "FAILURE", "FAILED" -> FAILURE;
            case "UNSTABLE" -> UNSTABLE;
            case "ABORTED" -> ABORTED;
            case "NOT_BUILT" -> NOT_BUILT;
            default -> UNKNOWN;
        };
    }
}
