package hr.tvz.popovic.dorasync.application.domain.model;

public record BuildNumber(long value) {

    public BuildNumber {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
    }
}
