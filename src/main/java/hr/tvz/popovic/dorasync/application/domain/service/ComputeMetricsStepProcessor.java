package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.domain.model.StepResult;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

public final class ComputeMetricsStepProcessor implements StepProcessor<JobStep.ComputeMetricsStep> {

    private final TransactionRunnerPort transactionRunner;

    public ComputeMetricsStepProcessor(TransactionRunnerPort transactionRunner) {
        this.transactionRunner = transactionRunner;
    }

    @Override
    public StepResult process(JobStep.ComputeMetricsStep step) {
        // TODO: link commits <-> builds <-> deployments and compute DORA metrics
        //       here, inside transactionRunner.inTransaction(...). DB-only.
        return new StepResult.Success();
    }
}
