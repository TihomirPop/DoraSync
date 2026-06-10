package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.CollectResult;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;

public final class GithubStepCollector {

    private final FetchConnectionPort fetchConnectionPort;

    public GithubStepCollector(FetchConnectionPort fetchConnectionPort) {
        this.fetchConnectionPort = fetchConnectionPort;
    }

    public CollectResult collect(JobStep.CollectGithubStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.GITHUB)) {
            case FetchConnectionPort.Result.Success(var serviceId, var externalReference) -> {
                // TODO: collect metrics from GitHub for serviceId + externalReference.
                yield new CollectResult.Success();
            }
            case FetchConnectionPort.Result.NotFound() ->
                    new CollectResult.Failure(new IllegalStateException("No GITHUB connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }
}
