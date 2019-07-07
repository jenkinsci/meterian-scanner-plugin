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
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LocalGitHubClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitHubClient.class);

    private static final String METERIAN_GITHUB_TOKEN_ABSENT_WARNING =
            "[meterian] Warning: METERIAN_GITHUB_TOKEN has not been set in the config (please check meterian settings in " +
                    "Manage Jenkins), cannot create pull request without this setting.";
    private static final String PULL_REQUEST_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. " +
                    "Fix already generated for current branch (commit point).";
    private static final String FOUND_PULL_REQUEST_WARNING =
            "[meterian] Warning: Found a pull request (id: %s) for org: %s, repo: %s, branch: %s";
    private static final String FINISHED_CREATING_PULL_REQUEST_MESSAGE = "[meterian] Finished creating pull request for org: %s, repo: %s, branch: %s.";

    private static final String METERIAN_FIX_PULL_REQUEST_TITLE = "[meterian] Fix for vulnerable dependencies";
    private static final String METERIAN_FIX_PULL_REQUEST_BODY = "Dependencies in project configuration file has been fixed";

    private static final String PULL_REQUEST_CREATION_ERROR = "Error occurred while creating pull request due to: %s";
    private static final String PULL_REQUEST_FETCHING_ACTION = "Fetching pull request(s) for org: %s, repo: %s, branch: %s";
    private static final String PULL_REQUEST_CREATION_ACTION = "Creating pull request for org: %s, repo: %s, branch: %s";
    private static final String PULL_REQUEST_FETCHING_ERROR = "Error occurred while fetching pull requests due to: %s";

    private static final boolean PULL_REQUEST_FOR_BRANCH_NOT_FOUND = false;
    private static final boolean PULL_REQUEST_FOR_BRANCH_FOUND = true;
    private static final String NO_PULL_REQUEST_ID_TO_RETURN = "";

    private final String orgOrUserName;
    private final String repoName;
    private final PrintStream jenkinsLogger;

    private GitHubClient github;
    private PullRequestService pullRequestService;

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
        if (pullRequestExists(branchName)) {
            log.warn(PULL_REQUEST_ALREADY_EXISTS_WARNING);
            jenkinsLogger.println(PULL_REQUEST_ALREADY_EXISTS_WARNING);
        } else {
            log.info(String.format(
                    PULL_REQUEST_CREATION_ACTION, orgOrUserName, repoName, branchName
            ));
            try {
                Repository repository = getRepositoryFrom(orgOrUserName, repoName);
                PullRequestMarker head = new PullRequestMarker()
                        .setRef(branchName)
                        .setLabel(String.format("%s:%s", orgOrUserName, branchName));
                PullRequestMarker base = new PullRequestMarker()
                        .setRef("master")
                        .setLabel("master");
                PullRequest pullRequest = new PullRequest()
                        .setTitle(METERIAN_FIX_PULL_REQUEST_TITLE)
                        .setHead(head)
                        .setBase(base)
                        .setBody(METERIAN_FIX_PULL_REQUEST_BODY);
                // See docs at https://developer.github.com/v3/pulls/#create-a-pull-request
                log.info(pullRequest.toString());
                pullRequestService.createPullRequest(repository, pullRequest);

                String finishedCreatingPullRequestMessage =
                        String.format(FINISHED_CREATING_PULL_REQUEST_MESSAGE, orgOrUserName, repoName, branchName);
                log.info(finishedCreatingPullRequestMessage);
                jenkinsLogger.println(finishedCreatingPullRequestMessage);
            } catch (Exception ex) {
                log.error(String.format(PULL_REQUEST_CREATION_ERROR, ex.getMessage()), ex);
                throw new RuntimeException(ex);
            }
        }
    }

    private boolean pullRequestExists(String branchName) {
        log.info(String.format(
                PULL_REQUEST_FETCHING_ACTION, orgOrUserName, repoName, branchName
        ));
        try {
            Repository repository = getRepositoryFrom(orgOrUserName, repoName);
            // See docs at https://developer.github.com/v3/pulls/#list-pull-requests
            String pullRequestId = getOpenPullRequestIdForBranch(repository, branchName);

            if (pullRequestId.isEmpty()) {
                return PULL_REQUEST_FOR_BRANCH_NOT_FOUND;
            }

            String foundPullRequestWarning = String.format(
                    FOUND_PULL_REQUEST_WARNING, pullRequestId, orgOrUserName, repoName, branchName
            );
            log.warn(foundPullRequestWarning);
            jenkinsLogger.println(foundPullRequestWarning);

            return PULL_REQUEST_FOR_BRANCH_FOUND;
        } catch (Exception ex) {
            log.error(String.format(PULL_REQUEST_FETCHING_ERROR, ex.getMessage()), ex);
            throw new RuntimeException(ex);
        }
    }

    private String getOpenPullRequestIdForBranch(Repository repository, String branchName) throws IOException {
        List<PullRequest> pullRequests = getAllOpenPullRequests(repository);
        Optional <PullRequest> foundPullRequest = pullRequests
                .stream()
                .filter(eachPullRequest ->
                        pullRequestWasCreatedAfterBranchWasCreated(
                                branchName,
                                eachPullRequest)
                ).findFirst();

        return foundPullRequest
                    .map(pullRequest -> String.valueOf(pullRequest.getNumber()))
                    .orElse(NO_PULL_REQUEST_ID_TO_RETURN);
    }

    private List<PullRequest> getAllOpenPullRequests(Repository repository) throws IOException {
        return pullRequestService.getPullRequests(repository, "open");
    }

    private boolean pullRequestWasCreatedAfterBranchWasCreated(String branchName,
                                                               PullRequest eachPullRequest) {
        return eachPullRequest
                .getHead()
                .getRef()
                .equals(branchName);
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
