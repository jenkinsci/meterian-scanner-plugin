package io.meterian.jenkins.github;

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.PullRequestMarker;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class LocalGitHubClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitHubClient.class);

    public static void main(String[] args) {
        new LocalGitHubClient().createPullRequest(
                System.getenv("GITHUB_TOKEN"),
                "MeterianHQ",
                "MeterianHQ/autofix-sample-maven-upgrade",
                "fixed-by-meterian-29c4d");
    }

    public void createPullRequest(String githubToken,
                                  String orgOrUserName,
                                  String repoName,
                                  String branchName) {
        log.info(String.format("Creating pull request for org: %s, repo: %s, branch: %s", orgOrUserName, repoName, branchName));
        try {
            GitHubClient github = new GitHubClient();
            github.setOAuth2Token(githubToken);

            RepositoryService repositoryService = new RepositoryService();
            List<Repository> repositories = repositoryService.getRepositories(orgOrUserName);
            Repository repository = repositories.stream()
                    .filter(repo -> repo.getCloneUrl().contains(repoName))
                    .collect(Collectors.toList())
                    .get(0);

            PullRequestService pullRequestService = new PullRequestService(github);
            String title = "[meterian] Fix for vulnerable dependencies";
            String body = "Dependencies in project configuration file has been fixed";
            PullRequestMarker head = new PullRequestMarker().setLabel(String.format("%s:%s", orgOrUserName, branchName));
            PullRequestMarker base = new PullRequestMarker().setLabel("master");
            PullRequest pullRequest = new PullRequest()
                                          .setTitle(title)
                                          .setHead(head)
                                          .setBase(base)
                                          .setBody(body);
            pullRequestService.createPullRequest(repository, pullRequest);
            log.info(String.format("Finished creating pull request for org: %s, repo: %s, branch: %s", orgOrUserName, repoName, branchName));
        } catch (Exception ex) {
            log.error("Error occurred while creating pull request due to: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
