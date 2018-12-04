package io.meterian.jenkins.github;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LocalGitHubClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitHubClient.class);

    public static void main(String[] args) throws IOException {
        new LocalGitHubClient().createPullRequest(
                System.getenv("GITHUB_TOKEN"),
                "MeterianHQ",
                "MeterianHQ/autofix-sample-maven-upgrade",
                "fixed-by-meterian-1617135076");
    }

    public void createPullRequest(String githubToken, String orgOrUserName, String repoName, String branchName) {
        log.info(String.format("Creating pull request for org: %s, repo: %s, branch: %s", orgOrUserName, repoName, branchName));
        try {
            GitHub github = GitHub.connectUsingOAuth(githubToken);
            GHRepository repository = github.getRepository(repoName);
            String title = "[meterian] Fix for vulnerable dependencies";
            String body = "Dependencies in pom.xml have been fixed";
            repository.createPullRequest(
                    title,
                    String.format("%s:%s", orgOrUserName, branchName),
                    "master",
                    body);
            log.info(String.format("Finished creating pull request for org: %s, repo: %s, branch: %s", orgOrUserName, repoName, branchName));
        } catch (Exception ex) {
            log.error("Error occurred while creating pull request due to: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
