package io.meterian.jenkins;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.git.LocalGitClient;
import io.meterian.jenkins.github.LocalGitHubClient;
import io.meterian.jenkins.glue.MeterianPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;

public class PullRequestCreator {

    private static final String LOCAL_BRANCH_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: %s already exists in the local repo, skipping the local branch creation process";

    static final Logger log = LoggerFactory.getLogger(PullRequestCreator.class);

    private final LocalGitClient localGitClient;
    private AbstractBuild build;
    private PrintStream jenkinsLogger;
    private MeterianPlugin.Configuration configuration;
    private Meterian client;

    public PullRequestCreator(
            AbstractBuild build,
            MeterianPlugin.Configuration configuration, String workspace,
            Meterian client, PrintStream jenkinsLogger) {
        this.build = build;
        this.configuration = configuration;
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;

        localGitClient = new LocalGitClient(workspace, jenkinsLogger);
    }

    public void execute() {
        try {
            if (localGitClient.localBranchDoesNotExists()) {
                log.info(localGitClient.getBranchName() + " does not exist in local repo");
                runMeterianClient(build, client);
                localGitClient.applyCommitsToLocalRepo();
            } else {
                String branchAlreadyExistsWarning =
                        String.format(LOCAL_BRANCH_ALREADY_EXISTS_WARNING, localGitClient.getBranchName());
                log.warn(branchAlreadyExistsWarning);
                jenkinsLogger.println(branchAlreadyExistsWarning);
            }

            localGitClient.pushBranchToRemoteRepo();

            LocalGitHubClient localGitHubClient = new LocalGitHubClient(
                    configuration.getGithubToken(),
                    localGitClient.getOrgOrUsername(),
                    localGitClient.getRepositoryName(),
                    jenkinsLogger
            );
            localGitHubClient.createPullRequest(localGitClient.getBranchName());
        } catch (
                Exception ex) {
            log.error("Pull Request was not created, due to the error: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    private void runMeterianClient(AbstractBuild build, Meterian client) throws IOException {
        Meterian.Result result = client.run();
        if (result.exitCode != 0) {
            build.setResult(Result.FAILURE);
        }
    }
}
