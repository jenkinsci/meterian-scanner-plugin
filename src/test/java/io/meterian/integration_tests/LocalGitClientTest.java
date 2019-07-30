package io.meterian.integration_tests;

import io.meterian.test_management.TestManagement;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class LocalGitClientTest {

    private static final Logger log = LoggerFactory.getLogger(LocalGitClientTest.class);

    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");

    private final String gitProtocol;
    private String githubOrgName = "MeterianHQ";
    private String githubProjectName = "autofix-sample-maven-upgrade";
    private String gitRepoRootFolder = Paths.get(CURRENT_WORKING_DIR, "target/github-repo-protocols/").toString();
    private String gitRepoWorkingFolder = Paths.get(gitRepoRootFolder, githubProjectName).toString();

    private File logFile;
    private PrintStream jenkinsLogger;

    @Parameterized.Parameters(name = "Cloning with git protocol: {0}")
    public static Collection gitProtocols() {
        // Git protocols: https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols
        return Arrays.asList(
                new Object[][] {
                        {"https"},
                        {"git@"},
                        {"ssh"},
                }
        );
    }

    public LocalGitClientTest(String gitProtocol) {
        this.gitProtocol = gitProtocol;
    }

    @Test
    public void givenAGitRepoConfiguredWithAGitProtocol_WhenLocalBranchIsPushToRemoteUsingTheToken_ThenRemoteIsCreated()
            throws IOException, GitAPIException {
        // Given
        logFile = File.createTempFile("jenkins-logger-", Long.toString(System.nanoTime()));
        jenkinsLogger = new PrintStream(logFile);
        log.info("Jenkins log file: " + logFile.toPath().toString());

        String expectedRemoteBranch = "dummy-remote-branch";

        FileUtils.deleteDirectory(new File(gitRepoRootFolder));
        new File(gitRepoRootFolder).mkdir();

        TestManagement testManagement = new TestManagement(gitRepoWorkingFolder, log, jenkinsLogger);
        testManagement.getConfiguration();
        testManagement.performCloneGitRepo(
                gitProtocol,
                githubProjectName,
                githubOrgName,
                gitRepoWorkingFolder,
                "master"
        );

        testManagement.setEnvironmentVariable("METERIAN_GITHUB_USER", "meterian-bot");
        testManagement.setEnvironmentVariable("METERIAN_GITHUB_EMAIL", "bot.bitbucket@meterian.io");

        // Deleting remote branch automatically closes any Pull Request attached to it
        testManagement.configureGitUserNameAndEmail(
                testManagement.getMeterianGithubUser() == null ? "meterian-bot" : testManagement.getMeterianGithubUser(),
                testManagement.getMeterianGithubEmail() == null ? "bot.github@meterian.io" : testManagement.getMeterianGithubEmail()
        );

        testManagement.checkoutBranch("master");
        testManagement.deleteLocalBranch(
                gitRepoWorkingFolder,
                expectedRemoteBranch
        );
        testManagement.deleteRemoteBranch(
                gitRepoWorkingFolder,
                githubOrgName,
                githubProjectName,
                expectedRemoteBranch
        );

        // When
        testManagement.createBranch(expectedRemoteBranch);
        testManagement.changeContentOfFile("README.md");
        testManagement.applyCommitsToLocalRepo();
        testManagement.pushBranchToRemoteRepo();

        // Then
        testManagement.checkoutBranch("master");
        testManagement.deleteLocalBranch(
                gitRepoWorkingFolder,
                expectedRemoteBranch
        );
        testManagement.verifyThatTheRemoteBranchWasCreated(expectedRemoteBranch);
        testManagement.deleteRemoteBranch(
                gitRepoWorkingFolder,
                githubOrgName,
                githubProjectName,
                expectedRemoteBranch
        );
    }
}