package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Deployment;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentCursor;
import hr.tvz.popovic.dorasync.application.domain.model.DeploykoService;
import hr.tvz.popovic.dorasync.application.domain.model.Id;

import java.util.List;

public interface DeploykoRepositoryPort {

    FindCursorResult findLatestRecordedAt(Id serviceConnectionId);

    UpsertTargetResult upsertTarget(Id serviceConnectionId, DeploykoService service);

    SaveDeploymentsResult saveDeployments(Id deploymentTargetId, List<Deployment> deployments);

    sealed interface FindCursorResult {

        record Success(DeploymentCursor cursor) implements FindCursorResult {
        }

        record Failure(Exception cause) implements FindCursorResult {
        }
    }

    sealed interface UpsertTargetResult {

        record Success(Id deploymentTargetId) implements UpsertTargetResult {
        }

        record Failure(Exception cause) implements UpsertTargetResult {
        }
    }

    sealed interface SaveDeploymentsResult {

        record Success(int savedCount) implements SaveDeploymentsResult {
        }

        record Failure(Exception cause) implements SaveDeploymentsResult {
        }
    }
}
