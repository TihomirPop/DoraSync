package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Deployment;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentCursor;
import hr.tvz.popovic.dorasync.application.domain.model.DeploykoService;

import java.util.List;

public interface FetchDeploykoDeploymentsPort {

    Result fetch(DeploykoService service, DeploymentCursor cursor);

    sealed interface Result {

        record Success(List<Deployment> deployments) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }
}
