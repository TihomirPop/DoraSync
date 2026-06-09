package hr.tvz.popovic.dorasync.application.port.out;

public interface TransactionRunnerPort {

    <T> Result<T> inTransaction(TransactionAction<T> action);

    @FunctionalInterface
    interface TransactionAction<T> {
        T execute(Transaction transaction);
    }

    interface Transaction {
        void rollback();
    }

    sealed interface Result<T> permits Result.Success, Result.Failure {

        record Success<T>(T value) implements Result<T> {
        }

        record Failure<T>(Throwable cause) implements Result<T> {
        }
    }
}