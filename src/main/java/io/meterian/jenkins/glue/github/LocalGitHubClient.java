package io.meterian.jenkins.glue.github;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;

public class LocalGitHubClient {
    public void createPullRequest(String githubToken, String repoName, String branchName) throws IOException {
        GitHub github = GitHub.connectUsingOAuth(githubToken);
        GHRepository repository = github.getRepository(repoName);
        String title = "[meterian] Fix for vulnerable dependencies";
        String body = "Dependencies in pom.xml have been fixed";
        repository.createPullRequest(title, branchName, "master", body);
    }
}
