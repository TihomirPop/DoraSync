package hr.tvz.popovic.dorasync.adapter.in;

import hr.tvz.popovic.dorasync.application.port.in.EnqueueScheduledJobsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobEnqueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobEnqueueScheduler.class);

    private final EnqueueScheduledJobsUseCase enqueueScheduledJobsUseCase;

    public ScheduledJobEnqueueScheduler(EnqueueScheduledJobsUseCase enqueueScheduledJobsUseCase) {
        this.enqueueScheduledJobsUseCase = enqueueScheduledJobsUseCase;
    }

    @Scheduled(fixedDelayString = "${dora-sync.scheduled-job-enqueuer.fixed-rate}")
    public void enqueueJobs() {
        var result = enqueueScheduledJobsUseCase.enqueue();
        switch (result) {
            case EnqueueScheduledJobsUseCase.Result.Success(var skippedServiceIds) -> {
                if (!skippedServiceIds.isEmpty()) {
                    log.info("Skipped enqueueing jobs for services with a job already running: {}", skippedServiceIds);
                }
            }
            case EnqueueScheduledJobsUseCase.Result.Failure(var message, var cause) -> log.error(message, cause);
        }
    }
}
