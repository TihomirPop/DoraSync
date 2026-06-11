package hr.tvz.popovic.dorasync.application.domain.model;

import java.util.Locale;

public enum StageStatus {

    SUCCESS,
    FAILED,
    UNSTABLE,
    ABORTED,
    NOT_EXECUTED,
    IN_PROGRESS,
    PAUSED,
    UNKNOWN;

    public static StageStatus from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SUCCESS" -> SUCCESS;
            case "FAILED", "FAILURE" -> FAILED;
            case "UNSTABLE" -> UNSTABLE;
            case "ABORTED" -> ABORTED;
            case "NOT_EXECUTED" -> NOT_EXECUTED;
            case "IN_PROGRESS" -> IN_PROGRESS;
            case "PAUSED" -> PAUSED;
            default -> UNKNOWN;
        };
    }
}
