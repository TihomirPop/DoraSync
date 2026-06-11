package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.CollectResult;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;

public final class JenkinsStepCollector {

    private final FetchConnectionPort fetchConnectionPort;

    public JenkinsStepCollector(FetchConnectionPort fetchConnectionPort) {
        this.fetchConnectionPort = fetchConnectionPort;
    }

    public CollectResult collect(JobStep.CollectJenkinsStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.JENKINS)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) -> {
                // TODO: collect metrics from Jenkins for serviceId + externalReference.
                yield new CollectResult.Success();
            }
            case FetchConnectionPort.Result.NotFound() ->
                    new CollectResult.Failure(new IllegalStateException("No JENKINS connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }
}
