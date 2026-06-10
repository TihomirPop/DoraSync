package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.port.in.ReapStaleJobsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.ReapStaleJobsPort;

import java.time.Instant;

public final class StaleJobReaper implements ReapStaleJobsUseCase {

    private final ReapStaleJobsPort reapStaleJobsPort;

    public StaleJobReaper(ReapStaleJobsPort reapStaleJobsPort) {
        this.reapStaleJobsPort = reapStaleJobsPort;
    }

    @Override
    public Result reap() {
        return switch (reapStaleJobsPort.reap(Instant.now())) {
            case ReapStaleJobsPort.Result.Success(var reapedCount) -> new Result.Success(reapedCount);
            case ReapStaleJobsPort.Result.Failure(var cause) -> new Result.Failure(cause);
        };
    }
}
