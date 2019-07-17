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

    private static final String ABORTING_BRANCH_AND_PR_CREATION_PROCESS = "[meterian] Aborting, not continuing with rest of the local/remote branch or pull request creation process.";

    private static final String LOCAL_BRANCH_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: %s already exists in the local repo, skipping the local branch creation process";

    private static final String BRANCH_ALREADY_FIXED_WARNING =
            "[meterian] Warning: %s is already fixed, no need to do anything";

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
                configuration.getMeterianGithubUser(),
                configuration.getMeterianGithubToken(),
                configuration.getMeterianGithubEmail(),
                jenkinsLogger);
    }

    public void execute() throws Exception {
        String targetBranchToWorkOn = localGitClient.getCurrentBranch();
        try {
            if (localGitClient.currentBranchWasCreatedByMeterianClient()) {
                targetBranchToWorkOn = localGitClient.getCurrentBranch();
                String thisBranchIsFixedMessage = String.format(
                        BRANCH_ALREADY_FIXED_WARNING, targetBranchToWorkOn
                );
                log.warn(thisBranchIsFixedMessage);
                jenkinsLogger.println(thisBranchIsFixedMessage);

                return;
            } else if (localGitClient.currentBranchHasNotBeenFixedYet()) {
                if (failedClientExecution()) {
                    localGitClient.resetChanges();

                    log.error(ABORTING_BRANCH_AND_PR_CREATION_PROCESS);
                    jenkinsLogger.println(ABORTING_BRANCH_AND_PR_CREATION_PROCESS);

                    return;
                }
            } else {
                clientRunner.breakBuild();

                targetBranchToWorkOn = localGitClient.getFixedBranchNameForCurrentBranch()
                                                     .replace("refs/heads/", "");

                String fixedBranchExistsMessage = String.format(
                        LOCAL_BRANCH_ALREADY_EXISTS_WARNING, targetBranchToWorkOn
                );
                log.warn(fixedBranchExistsMessage);
                jenkinsLogger.println(fixedBranchExistsMessage);

                localGitClient.checkoutBranch(targetBranchToWorkOn);
            }
        } catch (Exception ex) {
            log.error(String.format("Checking for branch or running the Meterian client was not successful due to: %s", ex.getMessage()), ex);
            throw new RuntimeException(ex);
        }

        try {
            if (localGitClient.hasChanges()) {
                localGitClient.applyCommitsToLocalRepo();
                targetBranchToWorkOn = localGitClient.getCurrentBranch();
            } else {
                log.warn(LocalGitClient.NO_CHANGES_FOUND_WARNING);
                jenkinsLogger.println(LocalGitClient.NO_CHANGES_FOUND_WARNING);
            }
        } catch (Exception ex) {
            log.error(String.format("Commits have not been applied due to the error: %s", ex.getMessage()), ex);
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
            localGitHubClient.createPullRequest(targetBranchToWorkOn);
        } catch (Exception ex) {
            log.error(String.format("Pull Request was not created, due to the error: %s", ex.getMessage()), ex);
            throw new RuntimeException(ex);
        }
    }

    private boolean failedClientExecution() {
        return clientRunner.execute() != 0;
    }
}
