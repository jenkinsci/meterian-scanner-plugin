package io.meterian.jenkins.github;

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.PullRequestMarker;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class LocalGitHubClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitHubClient.class);
    private final String orgOrUserName;
    private final String repoName;

    private GitHubClient github;
    private PullRequestService pullRequestService;

    public static void main(String[] args) {
        new LocalGitHubClient(
                System.getenv("GITHUB_TOKEN"),
                "MeterianHQ",
                "MeterianHQ/autofix-sample-maven-upgrade")
                .createPullRequest("fixed-by-meterian-29c4d");
    }

    public LocalGitHubClient(String gitHubToken,
                             String orgOrUserName,
                             String repoName) {
        this.orgOrUserName = orgOrUserName;
        this.repoName = repoName;

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
                pullRequestService.createPullRequest(repository, pullRequest);
                log.info(String.format(
                        "Finished creating pull request for org: %s, repo: %s, branch: %s", orgOrUserName, repoName, branchName
                ));
            } catch (Exception ex) {
                log.error("Error occurred while creating pull request due to: " + ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        }
        else {
            log.warn("Pull request already exists for this branch, no new pull request will be created");
        }
    }

    private boolean pullRequestDoesNotExist(String branchName) {
        log.info(String.format(
                "Fetching pull request for org: %s, repo: %s, branch: %s", orgOrUserName, repoName, branchName
        ));
        try {
            Repository repository = getRepositoryFrom(orgOrUserName, repoName);
            // See docs at https://developer.github.com/v3/pulls/#list-pull-requests
            List<PullRequest> pullRequestsFound = pullRequestService.getPullRequests(repository, "open");

            if (pullRequestsFound.size() > 0) {
                log.warn(String.format(
                        "Found %d pull request(s) for org: %s, repo: %s, branch: %s", pullRequestsFound.size(),
                        orgOrUserName, repoName, branchName
                ));
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
