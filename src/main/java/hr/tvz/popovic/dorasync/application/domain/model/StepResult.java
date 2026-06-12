package hr.tvz.popovic.dorasync.application.domain.model;

public sealed interface StepResult {

    record Success() implements StepResult {
    }

    record Failure(Exception cause) implements StepResult {
    }
}
