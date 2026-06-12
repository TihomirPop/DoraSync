package hr.tvz.popovic.dorasync.application.domain.model;

public sealed interface JobStep permits JobStep.CollectGithubStep, JobStep.CollectJenkinsStep, JobStep.CollectDeploykoStep, JobStep.ComputeMetricsStep {

    Id id();

    Id jobId();

    record CollectGithubStep(Id id, Id jobId) implements JobStep {
    }

    record CollectJenkinsStep(Id id, Id jobId) implements JobStep {
    }

    record CollectDeploykoStep(Id id, Id jobId) implements JobStep {
    }

    record ComputeMetricsStep(Id id, Id jobId) implements JobStep {
    }
}
