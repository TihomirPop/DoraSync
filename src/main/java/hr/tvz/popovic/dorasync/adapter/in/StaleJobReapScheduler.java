package hr.tvz.popovic.dorasync.adapter.in;

import hr.tvz.popovic.dorasync.application.port.in.ReapStaleJobsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaleJobReapScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleJobReapScheduler.class);

    private final ReapStaleJobsUseCase reapStaleJobsUseCase;

    public StaleJobReapScheduler(ReapStaleJobsUseCase reapStaleJobsUseCase) {
        this.reapStaleJobsUseCase = reapStaleJobsUseCase;
    }

    @Scheduled(fixedDelayString = "${dora-sync.stale-job-reaper.fixed-rate}")
    public void reapStaleJobs() {
        switch (reapStaleJobsUseCase.reap()) {
            case ReapStaleJobsUseCase.Result.Success(var reapedCount) -> {
                if (reapedCount > 0) {
                    log.info("Reaped {} stale job(s)", reapedCount);
                }
            }
            case ReapStaleJobsUseCase.Result.Failure(var cause) -> log.error("Failed to reap stale jobs", cause);
        }
    }
}
