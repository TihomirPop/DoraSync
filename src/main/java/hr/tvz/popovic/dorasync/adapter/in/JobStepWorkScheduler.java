package hr.tvz.popovic.dorasync.adapter.in;

import hr.tvz.popovic.dorasync.application.port.in.WorkJobStepsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobStepWorkScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobStepWorkScheduler.class);

    private final WorkJobStepsUseCase workJobStepsUseCase;

    public JobStepWorkScheduler(WorkJobStepsUseCase workJobStepsUseCase) {
        this.workJobStepsUseCase = workJobStepsUseCase;
    }

    @Scheduled(fixedDelayString = "${dora-sync.job-step-worker.fixed-rate}")
    public void workJobSteps() {
        switch (workJobStepsUseCase.work()) {
            case WorkJobStepsUseCase.Result.Success(var claimedCount) -> {
                if (claimedCount > 0) {
                    log.info("Claimed {} job step(s) for processing", claimedCount);
                }
            }
            case WorkJobStepsUseCase.Result.Failure(var cause) -> log.error("Failed to claim job steps", cause);
        }
    }
}
