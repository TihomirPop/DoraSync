package hr.tvz.popovic.dorasync.adapter.out.deployko;

import hr.tvz.popovic.dorasync.application.domain.model.Deployment;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentCursor;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentId;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentStatus;
import hr.tvz.popovic.dorasync.application.domain.model.DeploykoService;
import hr.tvz.popovic.dorasync.application.domain.model.ImageVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.port.out.FetchDeploykoDeploymentsPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class DeploykoRestClient implements FetchDeploykoDeploymentsPort {

    private final RestClient restClient;

    public DeploykoRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${dora-sync.deployko.base-url}") String baseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public Result fetch(DeploykoService service, DeploymentCursor cursor) {
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/services/{serviceName}/deployments");
                        if (cursor instanceof DeploymentCursor.Since(var recordedAt)) {
                            uriBuilder.queryParam("since", recordedAt.toString());
                        }
                        return uriBuilder.build(service.value());
                    })
                    .retrieve()
                    .body(DeploymentJson[].class);

            if (response == null) {
                return new Result.Success(List.of());
            }

            var deployments = Arrays.stream(response)
                    .map(DeploykoRestClient::toDeployment)
                    .toList();

            return new Result.Success(deployments);

        } catch (RuntimeException e) {
            return new Result.Failure(e);
        }
    }

    private static Deployment toDeployment(DeploymentJson json) {
        return new Deployment(
                new DeploymentId(json.deploymentId()),
                new ImageVersion(json.imageVersion()),
                Maybe.of(json.commitSha() == null ? null : new Sha(json.commitSha())),
                DeploymentStatus.from(json.status()),
                json.recordedAt()
        );
    }

    private record DeploymentJson(
            UUID deploymentId,
            String imageVersion,
            String commitSha,
            String status,
            Instant recordedAt
    ) {
    }
}
