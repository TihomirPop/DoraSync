package hr.tvz.popovic.dorasync.adapter.out.jenkins;

import com.fasterxml.jackson.annotation.JsonProperty;
import hr.tvz.popovic.dorasync.application.domain.model.Build;
import hr.tvz.popovic.dorasync.application.domain.model.BuildCursor;
import hr.tvz.popovic.dorasync.application.domain.model.BuildNumber;
import hr.tvz.popovic.dorasync.application.domain.model.BuildResult;
import hr.tvz.popovic.dorasync.application.domain.model.JobPath;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.domain.model.Stage;
import hr.tvz.popovic.dorasync.application.domain.model.StageStatus;
import hr.tvz.popovic.dorasync.application.port.out.FetchJenkinsBuildsPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JenkinsRestClient implements FetchJenkinsBuildsPort {

    private static final String BUILDS_TREE =
            "builds[number,result,timestamp,duration,actions[lastBuiltRevision[SHA1]]]";

    private final RestClient restClient;

    public JenkinsRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${dora-sync.jenkins.base-url}") String baseUrl,
            @Value("${dora-sync.jenkins.username}") String username,
            @Value("${dora-sync.jenkins.api-token}") String apiToken
    ) {
        var builder = restClientBuilder.baseUrl(baseUrl);
        if (!username.isBlank() || !apiToken.isBlank()) {
            builder = builder.defaultHeaders(headers -> headers.setBasicAuth(username, apiToken));
        }
        this.restClient = builder.build();
    }

    @Override
    public Result fetch(JobPath jobPath, BuildCursor cursor) {
        try {
            var apiPath = toApiPath(jobPath);
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + apiPath + "/api/json")
                            .queryParam("tree", BUILDS_TREE)
                            .build())
                    .retrieve()
                    .body(JobResponse.class);

            if (response == null || response.builds() == null) {
                return new Result.Success(List.of());
            }

            var builds = response.builds().stream()
                    .filter(build -> build.result() != null)
                    .filter(build -> isAfterCursor(build.number(), cursor))
                    .map(build -> toBuild(jobPath, build))
                    .toList();

            return new Result.Success(builds);

        } catch (RuntimeException e) {
            return new Result.Failure(e);
        }
    }

    private Build toBuild(JobPath jobPath, BuildJson build) {
        return new Build(
                new BuildNumber(build.number()),
                BuildResult.from(build.result()),
                build.duration() == null ? 0L : build.duration(),
                extractSha(build.actions()),
                Instant.ofEpochMilli(build.timestamp()),
                fetchStages(jobPath, build.number())
        );
    }

    private List<Stage> fetchStages(JobPath jobPath, long buildNumber) {
        try {
            var apiPath = toApiPath(jobPath);
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + apiPath + "/" + buildNumber + "/wfapi/describe")
                            .build())
                    .retrieve()
                    .body(WfApiResponse.class);

            if (response == null || response.stages() == null) {
                return List.of();
            }

            var stages = new ArrayList<Stage>();
            var stageJsons = response.stages();
            for (int order = 0; order < stageJsons.size(); order++) {
                var stage = stageJsons.get(order);
                stages.add(new Stage(
                        stage.name(),
                        StageStatus.from(stage.status()),
                        stage.durationMillis() == null ? 0L : stage.durationMillis(),
                        Maybe.of(stage.startTimeMillis() == null ? null : Instant.ofEpochMilli(stage.startTimeMillis())),
                        order
                ));
            }
            return stages;

        } catch (HttpClientErrorException e) {
            // Not a Pipeline job (or no workflow data for this build) -> no stages.
            return List.of();
        }
    }

    private static Maybe<Sha> extractSha(List<ActionJson> actions) {
        if (actions == null) {
            return new Maybe.None<>();
        }
        return actions.stream()
                .map(ActionJson::lastBuiltRevision)
                .filter(revision -> revision != null && revision.sha1() != null)
                .map(revision -> Maybe.of(new Sha(revision.sha1())))
                .findFirst()
                .orElseGet(Maybe.None::new);
    }

    /**
     * Maps the Jenkins job full name to its URL path segment:
     * {@code "DoraSync"} -> {@code "job/DoraSync"};
     * {@code "folder/DoraSync"} -> {@code "job/folder/job/DoraSync"}.
     */
    private static String toApiPath(JobPath jobPath) {
        return Arrays.stream(jobPath.value().split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> "job/" + segment)
                .collect(Collectors.joining("/"));
    }

    private static boolean isAfterCursor(long buildNumber, BuildCursor cursor) {
        return switch (cursor) {
            case BuildCursor.After(var latest) -> buildNumber > latest;
            case BuildCursor.Beginning() -> true;
        };
    }

    private record JobResponse(List<BuildJson> builds) {
    }

    private record BuildJson(
            long number,
            String result,
            long timestamp,
            Long duration,
            List<ActionJson> actions
    ) {
    }

    private record ActionJson(LastBuiltRevision lastBuiltRevision) {
    }

    private record LastBuiltRevision(@JsonProperty("SHA1") String sha1) {
    }

    private record WfApiResponse(List<StageJson> stages) {
    }

    private record StageJson(String name, String status, Long durationMillis, Long startTimeMillis) {
    }
}
