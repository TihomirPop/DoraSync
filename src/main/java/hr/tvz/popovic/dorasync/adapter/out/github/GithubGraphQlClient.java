package hr.tvz.popovic.dorasync.adapter.out.github;

import hr.tvz.popovic.dorasync.application.domain.model.Commit;
import hr.tvz.popovic.dorasync.application.domain.model.CommitCursor;
import hr.tvz.popovic.dorasync.application.domain.model.FullName;
import hr.tvz.popovic.dorasync.application.domain.model.GitIdentity;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.port.out.FetchGithubHistoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
public class GithubGraphQlClient implements FetchGithubHistoryPort {

    private static final int PAGE_SIZE = 100;

    private static final String HISTORY_QUERY = """
            query($owner: String!, $name: String!, $since: GitTimestamp, $after: String) {
              repository(owner: $owner, name: $name) {
                defaultBranchRef {
                  name
                  target {
                    ... on Commit {
                      history(since: $since, first: %d, after: $after) {
                        pageInfo { hasNextPage endCursor }
                        nodes {
                          oid
                          message
                          authoredDate
                          committedDate
                          additions
                          deletions
                          author    { name email }
                          committer { name email }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.formatted(PAGE_SIZE);

    private final HttpSyncGraphQlClient graphQlClient;

    public GithubGraphQlClient(
            RestClient.Builder restClientBuilder,
            @Value("${dora-sync.github.graphql-url}") String url,
            @Value("${dora-sync.github.token}") String token
    ) {
        var restClient = restClientBuilder
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        this.graphQlClient = HttpSyncGraphQlClient.create(restClient);
    }

    @Override
    public Result fetch(FullName fullName, CommitCursor cursor) {
        try {
            var commits = new ArrayList<Commit>();
            String defaultBranch = null;
            String after = null;

            while (true) {
                var ref = requestPage(fullName, cursor, after);
                if (ref == null || ref.target() == null || ref.target().history() == null) {
                    return new Result.Success(ref == null ? defaultBranch : ref.name(), commits);
                }

                defaultBranch = ref.name();
                var history = ref.target().history();
                history.nodes().stream().map(GithubGraphQlClient::toCommit).forEach(commits::add);

                if (!history.pageInfo().hasNextPage()) {
                    return new Result.Success(defaultBranch, commits);
                }
                after = history.pageInfo().endCursor();
            }

        } catch (RuntimeException e) {
            return new Result.Failure(e);
        }
    }

    private DefaultBranchRef requestPage(FullName fullName, CommitCursor cursor, String after) {
        var variables = new HashMap<String, Object>();
        variables.put("owner", fullName.owner());
        variables.put("name", fullName.name());
        variables.put("since", toSince(cursor));
        variables.put("after", after);

        var response = graphQlClient.document(HISTORY_QUERY).variables(variables).executeSync();

        if (!response.isValid()) {
            throw new IllegalStateException("GitHub GraphQL request failed: " + response.getErrors());
        }

        return response.field("repository.defaultBranchRef").toEntity(DefaultBranchRef.class);
    }

    private static String toSince(CommitCursor cursor) {
        return switch (cursor) {
            case CommitCursor.Since(var committedAt) -> committedAt.toString();
            case CommitCursor.Beginning() -> null;
        };
    }

    private static Commit toCommit(Node node) {
        return new Commit(
                new Sha(node.oid()),
                node.message(),
                node.authoredDate().toInstant(),
                node.committedDate().toInstant(),
                toIdentity(node.author()),
                toIdentity(node.committer()),
                node.additions(),
                node.deletions()
        );
    }

    private static GitIdentity toIdentity(Signature signature) {
        return signature == null ? null : new GitIdentity(signature.name(), signature.email());
    }

    private record DefaultBranchRef(String name, Target target) {
    }

    private record Target(History history) {
    }

    private record History(PageInfo pageInfo, List<Node> nodes) {
    }

    private record PageInfo(boolean hasNextPage, String endCursor) {
    }

    private record Node(
            String oid,
            String message,
            OffsetDateTime authoredDate,
            OffsetDateTime committedDate,
            Integer additions,
            Integer deletions,
            Signature author,
            Signature committer
    ) {
    }

    private record Signature(String name, String email) {
    }
}
