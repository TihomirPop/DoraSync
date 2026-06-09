package hr.tvz.popovic.dorasync.adapter.in;

import hr.tvz.popovic.dorasync.application.port.in.EnqueueScheduledJobsUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobEnqueueScheduler {

    private final EnqueueScheduledJobsUseCase enqueueScheduledJobsUseCase;

    public ScheduledJobEnqueueScheduler(EnqueueScheduledJobsUseCase enqueueScheduledJobsUseCase) {
        this.enqueueScheduledJobsUseCase = enqueueScheduledJobsUseCase;
    }

    @Scheduled(fixedDelayString = "${dora-sync.scheduled-job-enqueuer.fixed-rate}")
    public void enqueueJobs() {
        enqueueScheduledJobsUseCase.enqueue();
    }
}
