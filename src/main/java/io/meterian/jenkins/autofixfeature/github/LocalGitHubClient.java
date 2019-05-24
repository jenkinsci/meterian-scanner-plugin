package io.meterian.jenkins.autofixfeature.github;

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.PullRequestMarker;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

public class LocalGitHubClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitHubClient.class);

    private static final String METERIAN_GITHUB_TOKEN_ABSENT_WARNING =
            "[meterian] Warning: METERIAN_GITHUB_TOKEN has not been set in the config (please check meterian settings in " +
                    "Manage Jenkins), cannot create pull request.";
    private static final String PULL_REQUEST_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. " +
                    "Fixed already generated for current branch (commit point).";
    private static final String FOUND_PULL_REQUEST_WARNING =
            "[meterian] Warning: Found %d pull request(s) for org: %s, repo: %s, branch: %s";
    private static final String FINISHED_CREATING_PULL_REQUEST_MESSAGE = "[meterian] Finished creating pull request for org: %s, repo: %s, branch: %s.";

    private final String orgOrUserName;
    private final String repoName;
    private final PrintStream jenkinsLogger;

    private GitHubClient github;
    private PullRequestService pullRequestService;

    public static void main(String[] args) {
        PrintStream noOpStream = new PrintStream(new OutputStream() {
            public void write(int b) {
                // NO-OP
            }
        });
        new LocalGitHubClient(
                System.getenv("METERIAN_GITHUB_TOKEN"),
                "MeterianHQ",
                "MeterianHQ/autofix-sample-maven-upgrade",
                noOpStream).createPullRequest("fixed-by-meterian-29c4d26");
    }

    public LocalGitHubClient(String gitHubToken,
                             String orgOrUserName,
                             String repoName,
                             PrintStream jenkinsLogger) {
        this.orgOrUserName = orgOrUserName;
        this.repoName = repoName;
        this.jenkinsLogger = jenkinsLogger;

        if (gitHubToken == null || gitHubToken.isEmpty()) {
            log.warn(METERIAN_GITHUB_TOKEN_ABSENT_WARNING);
            jenkinsLogger.println(METERIAN_GITHUB_TOKEN_ABSENT_WARNING);
            return;
        }

        github = new GitHubClient();
        github.setOAuth2Token(gitHubToken);
        pullRequestService = new PullRequestService(github);
    }

    public void createPullRequest(String branchName) {
        if (pullRequestDoesNotExist(branchName)) {
            log.info(String.format(
                    "Creating pull request for org: %s, repo: %s, branch: %s", orgOrUserName, repoName, branchName
            ));
            try {
                Repository repository = getRepositoryFrom(orgOrUserName, repoName);
                String title = "[meterian] Fix for vulnerable dependencies";
                String body = "Dependencies in project configuration file has been fixed";
                PullRequestMarker head = new PullRequestMarker().setLabel(
                        String.format("%s:%s", orgOrUserName, branchName)
                );
                PullRequestMarker base = new PullRequestMarker().setLabel("master");
                PullRequest pullRequest = new PullRequest()
                        .setTitle(title)
                        .setHead(head)
                        .setBase(base)
                        .setBody(body);
                // See docs at https://developer.github.com/v3/pulls/#create-a-pull-request
                log.info(pullRequest.toString());
                pullRequestService.createPullRequest(repository, pullRequest);

                String finishedCreatingPullRequestMessage =
                        String.format(FINISHED_CREATING_PULL_REQUEST_MESSAGE, orgOrUserName, repoName, branchName);
                log.info(finishedCreatingPullRequestMessage);
                jenkinsLogger.println(finishedCreatingPullRequestMessage);
            } catch (Exception ex) {
                log.error("Error occurred while creating pull request due to: " + ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        } else {
            log.warn(PULL_REQUEST_ALREADY_EXISTS_WARNING);
            jenkinsLogger.println(PULL_REQUEST_ALREADY_EXISTS_WARNING);
        }
    }

    private boolean pullRequestDoesNotExist(String branchName) {
        log.info(String.format(
                "Fetching pull request(s) for org: %s, repo: %s, branch: %s", orgOrUserName, repoName, branchName
        ));
        try {
            Repository repository = getRepositoryFrom(orgOrUserName, repoName);
            // See docs at https://developer.github.com/v3/pulls/#list-pull-requests
            List<PullRequest> pullRequestsFound = pullRequestService.getPullRequests(repository, "open");

            if (pullRequestsFound.size() > 0) {
                String foundPullRequestWarning = String.format(
                        FOUND_PULL_REQUEST_WARNING, pullRequestsFound.size(), orgOrUserName, repoName, branchName
                );
                log.warn(foundPullRequestWarning);
                jenkinsLogger.println(foundPullRequestWarning);
            }
            return pullRequestsFound.size() == 0;
        } catch (Exception ex) {
            log.error("Error occurred while fetching pull requests due to: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    private Repository getRepositoryFrom(String orgOrUserName, String repoName) throws IOException {
        RepositoryService repositoryService = new RepositoryService();
        List<Repository> repositories = repositoryService.getRepositories(orgOrUserName);
        return repositories.stream()
                .filter(repo -> repo.getCloneUrl().contains(repoName))
                .collect(Collectors.toList())
                .get(0);
    }
}
