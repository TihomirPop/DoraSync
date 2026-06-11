package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.CollectResult;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;

public final class DeploykoStepCollector {

    private final FetchConnectionPort fetchConnectionPort;

    public DeploykoStepCollector(FetchConnectionPort fetchConnectionPort) {
        this.fetchConnectionPort = fetchConnectionPort;
    }

    public CollectResult collect(JobStep.CollectDeploykoStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.DEPLOYKO)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) -> {
                // TODO: collect metrics from Deployko for serviceId + externalReference.
                yield new CollectResult.Success();
            }
            case FetchConnectionPort.Result.NotFound() ->
                    new CollectResult.Failure(new IllegalStateException("No DEPLOYKO connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }
}
