package hr.tvz.popovic.dorasync.application.domain.model;

public sealed interface CollectResult {

    record Success() implements CollectResult {
    }

    record Failure(Exception cause) implements CollectResult {
    }
}
