package io.meterian.jenkins;

import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.git.LocalGitClient;
import io.meterian.jenkins.github.LocalGitHubClient;
import io.meterian.jenkins.glue.MeterianPlugin;
import io.meterian.jenkins.glue.executors.MeterianExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class PullRequestCreator {

    private static final String LOCAL_BRANCH_ALREADY_EXISTS_WARNING =
            "[meterian] Warning: %s already exists in the local repo, skipping the local branch creation process";

    static final Logger log = LoggerFactory.getLogger(PullRequestCreator.class);

    private final LocalGitClient localGitClient;
    private MeterianExecutor executor;
    private PrintStream jenkinsLogger;
    private MeterianPlugin.Configuration configuration;
    private Meterian client;

    public PullRequestCreator(
            MeterianPlugin.Configuration configuration,
            String workspace,
            Meterian client,
            MeterianExecutor executor,
            PrintStream jenkinsLogger) {
        this.configuration = configuration;
        this.client = client;
        this.executor = executor;
        this.jenkinsLogger = jenkinsLogger;

        localGitClient = new LocalGitClient(workspace, jenkinsLogger);
    }

    public PullRequestCreator(MeterianPlugin.Configuration configuration,
                              String workspace,
                              Meterian client,
                              PrintStream jenkinsLogger) {
        this.configuration = configuration;
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;

        localGitClient = new LocalGitClient(workspace, jenkinsLogger);
    }

    public Meterian.Result execute() {
        Meterian.Result buildResult = null;
        try {
            if (localGitClient.localBranchDoesNotExists()) {
                log.info(localGitClient.getBranchName() + " does not exist in local repo");
                //TODO: needs refactoring, improvement in design
                if (executor == null) {
                    buildResult = client.run();
                } else {
                    try {
                        executor.run(client);
                    } catch (Exception ex) {
                        log.warn("Unexpected", ex);
                        jenkinsLogger.println("Unexpected exception!");
                        ex.printStackTrace(jenkinsLogger);
                    }
                }
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

        return buildResult;
    }
}
