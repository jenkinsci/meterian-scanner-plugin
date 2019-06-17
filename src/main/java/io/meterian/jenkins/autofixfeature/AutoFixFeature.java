package io.meterian.jenkins.autofixfeature;

import hudson.EnvVars;
import io.meterian.jenkins.autofixfeature.git.LocalGitClient;
import io.meterian.jenkins.autofixfeature.github.LocalGitHubClient;
import io.meterian.jenkins.glue.MeterianPlugin;
import io.meterian.jenkins.glue.clientrunners.ClientRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class AutoFixFeature {

    private static final String LOCAL_BRANCH_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: %s already exists in the local repo, skipping the local branch creation process";

    static final Logger log = LoggerFactory.getLogger(AutoFixFeature.class);

    private final LocalGitClient localGitClient;
    private ClientRunner clientRunner;
    private PrintStream jenkinsLogger;
    private MeterianPlugin.Configuration configuration;

    public AutoFixFeature(MeterianPlugin.Configuration configuration,
                          EnvVars environment,
                          ClientRunner clientRunner,
                          PrintStream jenkinsLogger) {
        this.configuration = configuration;
        this.clientRunner = clientRunner;
        this.jenkinsLogger = jenkinsLogger;

        localGitClient = new LocalGitClient(
                environment.get("WORKSPACE"),
                environment.get("METERIAN_GITHUB_USER"),
                environment.get("METERIAN_GITHUB_EMAIL"),
                jenkinsLogger);
    }

    public void execute() {
        try {
            if (localGitClient.currentBranchHasNotBeenFixedYet()) {
                clientRunner.execute();
            } else {
                String fixedBranchExistsMessage = String.format(
                                LOCAL_BRANCH_ALREADY_EXISTS_WARNING, localGitClient.getFixedBranchNameForCurrentBranch()
                );
                log.warn(fixedBranchExistsMessage);
                jenkinsLogger.println(fixedBranchExistsMessage);
            }
        } catch (Exception ex) {
            log.error("Checking for branch or running the Meterian client was not successful due to: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }

        try {
            if (localGitClient.hasChanges()) {
                localGitClient.applyCommitsToLocalRepo();
            } else {
                log.warn(LocalGitClient.NO_CHANGES_FOUND_WARNING);
                jenkinsLogger.println(LocalGitClient.NO_CHANGES_FOUND_WARNING);
            }
        } catch (Exception ex) {
            log.error("Commits have not been applied due to the error: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }

        localGitClient.pushBranchToRemoteRepo();

        try {
            LocalGitHubClient localGitHubClient = new LocalGitHubClient(
                    configuration.getMeterianGithubToken(),
                    localGitClient.getOrgOrUsername(),
                    localGitClient.getRepositoryName(),
                    jenkinsLogger
            );
            localGitHubClient.createPullRequest(localGitClient.getCurrentBranch());
        } catch (Exception ex) {
            log.error("Pull Request was not created, due to the error: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
