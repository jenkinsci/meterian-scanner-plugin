package io.meterian.jenkins.autofixfeature;

import io.meterian.jenkins.autofixfeature.git.LocalGitClient;
import io.meterian.jenkins.autofixfeature.github.LocalGitHubClient;
import io.meterian.jenkins.glue.ClientRunner;
import io.meterian.jenkins.glue.MeterianPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class PullRequestCreator {

    private static final String LOCAL_BRANCH_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: %s already exists in the local repo, skipping the local branch creation process";

    static final Logger log = LoggerFactory.getLogger(PullRequestCreator.class);

    private final LocalGitClient localGitClient;
    private ClientRunner clientRunner;
    private PrintStream jenkinsLogger;
    private MeterianPlugin.Configuration configuration;

    public PullRequestCreator(MeterianPlugin.Configuration configuration,
                              String workspace,
                              ClientRunner clientRunner,
                              PrintStream jenkinsLogger) {
        this.configuration = configuration;
        this.clientRunner = clientRunner;
        this.jenkinsLogger = jenkinsLogger;

        localGitClient = new LocalGitClient(workspace, jenkinsLogger);
    }

    public void execute() {
        try {
            if (localGitClient.localBranchDoesNotExists()) {
                log.info(localGitClient.getBranchName() + " does not exist in local repo");
                clientRunner.execute();
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
}
