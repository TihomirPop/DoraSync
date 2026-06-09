package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactionRunner implements TransactionRunnerPort {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> Result<T> inTransaction(TransactionAction<T> action) {
        try {
            return transactionTemplate.execute(status -> {
                Transaction transaction = status::setRollbackOnly;

                T value = action.execute(transaction);

                return new Result.Success<>(value);
            });
        } catch (Exception e) {
            return new Result.Failure<>(e);
        }
    }
}