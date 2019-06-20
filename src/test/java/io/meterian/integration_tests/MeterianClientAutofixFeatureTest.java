package io.meterian.integration_tests;

import io.meterian.jenkins.autofixfeature.git.LocalGitClient;
import io.meterian.jenkins.glue.MeterianPlugin;
import io.meterian.test_management.TestManagement;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import hudson.EnvVars;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MeterianClientAutofixFeatureTest {

    private static final Logger log = LoggerFactory.getLogger(MeterianClientAutofixFeatureTest.class);

    private TestManagement testManagement;
    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");

    private MeterianPlugin.Configuration configuration;
    private EnvVars environment;

    private String githubProjectName = "autofix-sample-maven-upgrade";
    private String gitRepoRootFolder = Paths.get(CURRENT_WORKING_DIR, "target/github-repo/").toString();
    private String gitRepoWorkingFolder = Paths.get(gitRepoRootFolder, githubProjectName).toString();
    private File logFile;
    private PrintStream jenkinsLogger;

    @Before
    public void setup() throws IOException {
        logFile = File.createTempFile("jenkins-logger-", Long.toString(System.nanoTime()));
        jenkinsLogger = new PrintStream(logFile);
        log.info("Jenkins log file: " + logFile.toPath().toString());

        testManagement = new TestManagement(gitRepoWorkingFolder, log, jenkinsLogger);

        environment = testManagement.getEnvironment();
        configuration = testManagement.getConfiguration();
    }

    @Test
    public void scenario1_givenConfiguration_whenMeterianClientIsRunWithAutofixOptionForTheFirstTime_thenItShouldReturnAnalysisReportAndFixThem() throws IOException {
        // Given: we are setup to run the meterian client against a repo that has vulnerabilities
        FileUtils.deleteDirectory(new File(gitRepoRootFolder));
        new File(gitRepoRootFolder).mkdir();
        testManagement.performCloneGitRepo("MeterianHQ", githubProjectName, gitRepoRootFolder, "master");

        // Deleting remote branch automatically closes any Pull Request attached to it
        testManagement.configureGitUserNameAndEmail(
                testManagement.getMeterianGithubUser() == null ? "meterian-bot" : testManagement.getMeterianGithubUser(),
                testManagement.getMeterianGithubEmail() == null ? "bot.github@meterian.io" : testManagement.getMeterianGithubEmail()
        );
        testManagement.deleteRemoteBranch("fixed-by-meterian-196350c");

        // When: the meterian client is run against the locally cloned git repo with the autofix feature (--autofix) passed as a CLI arg
        testManagement.runMeterianClientAndReportAnalysis(configuration, jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and the
        // reported vulnerabilities should be fixed, the changes committed to a branch and a pull request
        // created onto the respective remote Github repository of the project
        testManagement.verifyRunAnalysisLogs(logFile,
            new String[]{
                "[meterian] Client successfully authorized",
                "[meterian] Meterian Client v",
                "[meterian] - autofix mode:      on",
                "[meterian] Running autofix,",
                "[meterian] Autofix applied, will run the build again.",
                "[meterian] Project information:",
                "[meterian] JAVA scan -",
                "MeterianHQ/autofix-sample-maven-upgrade.git",
                "[meterian] Full report available at: ",
                "[meterian] Final results:",
                "[meterian] - security:	100	(minimum: 90)",
                "[meterian] - stability:	100	(minimum: 80)",
                "[meterian] - licensing:	99	(minimum: 95)",
                "[meterian] Build successful!",
                "[meterian] Finished creating pull request for org: MeterianHQ, repo: MeterianHQ/autofix-sample-maven-upgrade, branch: fixed-by-meterian-196350c."
            },
            new String[]{
                "Meterian client analysis failed with exit code ",
                "[meterian] Breaking build",
                "[meterian] Aborting, not continuing with rest of the local/remote branch or pull request creation process."
            }
        );
    }

    @Test
    public void scenario2_givenMeterianClientHasBeenRunAsInStep1_whenMeterianClientIsReRunWithAutofixOptionOnAMeterianFixedBranch_thenItShouldDoNothing() throws IOException, GitAPIException {
        // Given: the Meterian Client has been run once before (in step 1 of the test)
        // a local branch called fixed-by-meterian-196350c already exists
        // a remote branch called fixed-by-meterian-196350c already exists
        // a pull request attached to remote branch fixed-by-meterian-196350c also exists
        // and the current branch is fixed-by-meterian-196350c
        testManagement.checkoutBranch("fixed-by-meterian-196350c");

        // When: meterian client is run on the current branch 'fixed-by-meterian-196350c' (which has already been fixed before)
        testManagement.runMeterianClientAndReportAnalysis(configuration, jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and no action should
        // be taken by it, it should report warnings of the presence of the local branch with the fixes, the remote branch
        // with the fixes and also the pull request attached to this remote branch
        // and the build should fail as the current branch isn't fixed yet a PR is still pending
        // build should NOT break
        testManagement.verifyRunAnalysisLogs(logFile,
            new String[]{
                "Warning: fixed-by-meterian-196350c is already fixed, no need to do anything",
            },
            new String[]{
                "No changes found (no fixes generated), no branch to push to the remote repo.",
                "[meterian] Warning: fixed-by-meterian-196350c already exists in the remote repo, skipping the remote branch creation process.",
                "[meterian] Warning: Found 1 pull request(s) for org: MeterianHQ, repo: MeterianHQ/autofix-sample-maven-upgrade, branch: fixed-by-meterian-196350c",
                "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. Fixed already generated for current branch (commit point).",
                "Meterian client analysis failed with exit code ",
                "[meterian] Breaking build",
                "[meterian] Aborting, not continuing with rest of the local/remote branch or pull request creation process."
            }
        );
    }

    @Test
    public void scenario3_givenMeterianClientHasBeenRunAsInStep1_whenMeterianClientIsReRunWithAutofixOptionOnTheSameBranch_thenItShouldDoNothingBuildShouldFail() throws IOException, GitAPIException {
        // Given: the Meterian Client has been run once before (in step 1 of the test)
        // a local branch called 'master' and has a local fixed branch called fixed-by-meterian-196350c,
        // may or may not have a remote branch with the same name and/or a pull request attached to remote branch
        // fixed-by-meterian-196350c
        testManagement.resetBranch("master");
        String expectedBranch = "fixed-by-meterian-196350c";
        assertThat("Cannot run the test as the expected branch "+ expectedBranch + " does not exists",
                testManagement.branchExists(expectedBranch), is(true));

        // When: meterian client is run on the current branch 'master' (which has already has a fixed local branch)
        testManagement.runMeterianClientAndReportAnalysis(configuration, jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and no action should
        // be taken by it. No changes should be committed. It should report warnings of the presence of the local branch
        // with the fixes, the remote branch (if present) and/or pull request (if present)
        // build should break, since the pull request is not yet merged (pending merge)
        testManagement.verifyRunAnalysisLogs(logFile,
                new String[]{
                        "[meterian] Warning: refs/heads/fixed-by-meterian-196350c already exists in the local repo, skipping the local branch creation process",
                        "No changes found (no fixes generated), no branch to push to the remote repo.",
                        "[meterian] Warning: Found 1 pull request(s) for org: MeterianHQ, repo: MeterianHQ/autofix-sample-maven-upgrade, branch: fixed-by-meterian-196350c",
                        "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. Fixed already generated for current branch (commit point).",
                        "[meterian] Breaking build"
                },
                new String[]{
                        "[meterian] Aborting, not continuing with rest of the local/remote branch or pull request creation process."
                }
        );
    }

    @Test
    public void scenario4_givenConfiguration_whenMeterianClientIsRunWithAutofixOptionForTheFirstTime_thenItShouldReturnAnalysisReportAndPartiallyFixThem() throws IOException, GitAPIException {
        // Given: we are setup to run the meterian client against a repo that has vulnerabilities
        FileUtils.deleteDirectory(new File(gitRepoRootFolder));
        new File(gitRepoRootFolder).mkdir();
        testManagement.performCloneGitRepo(
                "MeterianHQ", githubProjectName, gitRepoRootFolder, "partially-fixed-by-autofix");

        // Deleting remote branch automatically closes any Pull Request attached to it
        testManagement.configureGitUserNameAndEmail(
                testManagement.getMeterianGithubUser() == null ? "meterian-bot" : testManagement.getMeterianGithubUser(),
                testManagement.getMeterianGithubEmail() == null ? "bot.github@meterian.io" : testManagement.getMeterianGithubEmail()
        );

        // When: the meterian client is run against the locally cloned git repo with the autofix feature (--autofix) passed as a CLI arg
        testManagement.runMeterianClientAndReportAnalysis(configuration, jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and the
        // reported vulnerabilities should NOT be fully fixed, NO changes must be committed to a local or remote branch
        // and NO pull request must be created onto the respective remote Github repository of the project
        // build should break
        testManagement.verifyRunAnalysisLogs(logFile,
                new String[]{
                        "[meterian] Client successfully authorized",
                        "[meterian] Meterian Client v",
                        "[meterian] - autofix mode:      on",
                        "[meterian] Running autofix,",
                        "[meterian] Autofix applied, will run the build again.",
                        "[meterian] Project information:",
                        "[meterian] JAVA scan -",
                        "MeterianHQ/autofix-sample-maven-upgrade.git",
                        "[meterian] Execution successful!",
                        "[meterian] Final results:",
                        "[meterian] - security:\t35\t(minimum: 90)",
                        "[meterian] - stability:\t100\t(minimum: 80)",
                        "[meterian] - licensing:\t99\t(minimum: 95)",
                        "[meterian] Build unsuccessful!",
                        "[meterian] Failed checks: [security]",
                        "Meterian client analysis failed with exit code 1",
                        "[meterian] Breaking build",
                        "[meterian] Aborting, not continuing with rest of the local/remote branch or pull request creation process."
                },
                new String[]{
                        "Finished creating pull request for org: MeterianHQ, repo: MeterianHQ/autofix-sample-maven-upgrade, branch: fixed-by-meterian-",
                        "[meterian] Warning: fixed-by-meterian-" +
                        "already exists in the remote repo, skipping the remote branch creation process.",
                        "[meterian] Warning: Found 1 pull request(s) for org: MeterianHQ, repo: MeterianHQ/autofix-sample-maven-upgrade, branch: fixed-by-meterian-",
                        "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. Fixed already generated for current branch (commit point)."
                }
        );
        LocalGitClient gitClient = new LocalGitClient(
                environment.get("WORKSPACE"),
                testManagement.getMeterianGithubUser(),
                testManagement.getMeterianGithubEmail(),
                jenkinsLogger
        );
        assertThat("Should have had nothing to commit on current branch",
                gitClient.hasChanges(), is(false));
        assertThat("Should have been positioned at the current branch by the name 'partially-fixed-by-autofix'",
                gitClient.getCurrentBranch(),
                is(equalTo("partially-fixed-by-autofix")));
    }
}