package integration_tests;

import com.meterian.common.system.LineGobbler;
import com.meterian.common.system.OS;
import com.meterian.common.system.Shell;
import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import io.meterian.jenkins.autofixfeature.AutoFixFeature;
import io.meterian.jenkins.autofixfeature.git.LocalGitClient;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.MeterianPlugin;
import io.meterian.jenkins.glue.clientrunners.ClientRunner;
import io.meterian.jenkins.glue.executors.MeterianExecutor;
import io.meterian.jenkins.glue.executors.StandardExecutor;
import io.meterian.jenkins.io.ClientDownloader;
import io.meterian.jenkins.io.HttpClientFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.client.HttpClient;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
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
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MeterianClientAutofixFeatureTest {

    private static final Logger log = LoggerFactory.getLogger(MeterianClientAutofixFeatureTest.class);

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");

    private static final String NO_JVM_ARGS = "";

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

        environment = getEnvironment();
        String meterianAPIToken = environment.get("METERIAN_API_TOKEN");
        assertThat("METERIAN_API_TOKEN has not been set, cannot run test without a valid value", meterianAPIToken, notNullValue());

        String meterianGithubUser = getMeterianGithubUser();
        if ((meterianGithubUser == null) || meterianGithubUser.trim().isEmpty()) {
            jenkinsLogger.println("METERIAN_GITHUB_USER has not been set, tests will be run using the default value assumed for this environment variable");
        }

        String meterianGithubEmail = getMeterianGithubEmail();
        if ((meterianGithubEmail == null) || meterianGithubEmail.trim().isEmpty()) {
            jenkinsLogger.println("METERIAN_GITHUB_EMAIL has not been set, tests will be run using the default value assumed for this environment variable");
        }

        String meterianGithubToken = environment.get("METERIAN_GITHUB_TOKEN");
        assertThat("METERIAN_GITHUB_TOKEN has not been set, cannot run test without a valid value", meterianGithubToken, notNullValue());

        configuration = new MeterianPlugin.Configuration(
                BASE_URL,
                meterianAPIToken,
                NO_JVM_ARGS,
                meterianGithubUser,
                meterianGithubEmail,
                meterianGithubToken
        );
    }

    @Test
    public void scenario1_givenConfiguration_whenMeterianClientIsRunWithAutofixOptionForTheFirstTime_thenItShouldReturnAnalysisReportAndFixThem() throws IOException {
        // Given: we are setup to run the meterian client against a repo that has vulnerabilities
        FileUtils.deleteDirectory(new File(gitRepoRootFolder));
        new File(gitRepoRootFolder).mkdir();
        performCloneGitRepo("MeterianHQ", githubProjectName, gitRepoRootFolder, "master");

        // Deleting remote branch automatically closes any Pull Request attached to it
        configureGitUserNameAndEmail(
                getMeterianGithubUser() == null ? "meterian-bot" : getMeterianGithubUser(),
                getMeterianGithubEmail() == null ? "bot.github@meterian.io" : getMeterianGithubEmail()
        );
        deleteRemoteBranch("fixed-by-meterian-196350c");

        // When: the meterian client is run against the locally cloned git repo with the autofix feature (--autofix) passed as a CLI arg
        runMeterianClientAndReportAnalysis(jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and the
        // reported vulnerabilities should be fixed, the changes committed to a branch and a pull request
        // created onto the respective remote Github repository of the project
        verifyRunAnalysisLogs(logFile,
            new String[]{
                "[meterian] Client successfully authorized",
                "[meterian] Meterian Client v",
                "[meterian] - autofix mode:      on",
                "[meterian] Running autofix, 1 programs",
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
                    "[meterian] Aborting, not continuing with rest of the local/remote branch or pull request creation process."
            }
        );
    }

    @Test
    public void scenario2_givenMeterianClientHasBeenRunAsInStep1_whenMeterianClientIsReRunWithAutofixOptionOnAFixedBranch_thenItShouldDoNothing() throws IOException {
        // Given: the Meterian Client has been run once before (in step 1 of the test)
        // a local branch called fixed-by-meterian-196350c already exists
        // a remote branch called fixed-by-meterian-196350c already exists
        // a pull request attached to remote branch fixed-by-meterian-196350c also exists
        // and the current branch is fixed-by-meterian-196350c

        // When: meterian client is run on the current branch (which has already been fixed before)
        runMeterianClientAndReportAnalysis(jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and no action should
        // be taken by it, it should report warnings of the presence of the local branch with the fixes, the remote branch
        // with the fixes and also the pull request attached to this remote branch
        verifyRunAnalysisLogs(logFile,
            new String[]{
                "[meterian] Client successfully authorized",
                "[meterian] Meterian Client v",
                "[meterian] - autofix mode:      on",
                "[meterian] Running autofix, 1 programs",
                "[meterian] Project information:",
                "[meterian] JAVA scan -",
                "MeterianHQ/autofix-sample-maven-upgrade.git",
                "[meterian] Full report available at: ",
                "[meterian] Final results:",
                "[meterian] - security:	100	(minimum: 90)",
                "[meterian] - stability:	100	(minimum: 80)",
                "[meterian] - licensing:	99	(minimum: 95)",
                "No changes found (no fixes generated), no branch to push to the remote repo.",
                "[meterian] Warning: fixed-by-meterian-196350c already exists in the remote repo, skipping the remote branch creation process.",
                "[meterian] Warning: Found 1 pull request(s) for org: MeterianHQ, repo: MeterianHQ/autofix-sample-maven-upgrade, branch: fixed-by-meterian-196350c",
                "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. Fixed already generated for current branch (commit point)."
            },
            new String[]{
                "Meterian client analysis failed with exit code ",
                "[meterian] Aborting, not continuing with rest of the local/remote branch or pull request creation process."
            }
        );
    }

    private void runMeterianClientAndReportAnalysis(PrintStream jenkinsLogger) {
        try {
            File clientJar = new ClientDownloader(newHttpClient(), BASE_URL, nullPrintStream()).load();
            Meterian client = Meterian.build(configuration, environment, jenkinsLogger, NO_JVM_ARGS, clientJar);
            client.prepare("--interactive=false", "--autofix");

            ClientRunner clientRunner =
                    new ClientRunner(client, mock(StepContext.class), jenkinsLogger);

            AutoFixFeature autoFixFeature = new AutoFixFeature(
                    configuration,
                    environment,
                    clientRunner,
                    jenkinsLogger
            );
            MeterianExecutor executor = new StandardExecutor(clientRunner, autoFixFeature);
            executor.run(client);
            jenkinsLogger.close();

        } catch (Exception ex) {
            fail("Should not have failed with the exception: " + ex.getMessage());
        }
    }

    @Test
    public void scenario3_givenConfiguration_whenMeterianClientIsRunWithAutofixOptionForTheFirstTime_thenItShouldReturnAnalysisReportAndPartiallyFixThem() throws IOException, GitAPIException {
        // Given: we are setup to run the meterian client against a repo that has vulnerabilities
        FileUtils.deleteDirectory(new File(gitRepoRootFolder));
        new File(gitRepoRootFolder).mkdir();
        performCloneGitRepo(
                "MeterianHQ", githubProjectName, gitRepoRootFolder, "partially-fixed-by-autofix");

        // Deleting remote branch automatically closes any Pull Request attached to it
        configureGitUserNameAndEmail(
                getMeterianGithubUser() == null ? "meterian-bot" : getMeterianGithubUser(),
                getMeterianGithubEmail() == null ? "bot.github@meterian.io" : getMeterianGithubEmail()
        );

        // When: the meterian client is run against the locally cloned git repo with the autofix feature (--autofix) passed as a CLI arg
        runMeterianClientAndReportAnalysis(jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and the
        // reported vulnerabilities should NOT be fully fixed, NO changes must be committed to a local or remote branch
        // and NO pull request must be created onto the respective remote Github repository of the project
        verifyRunAnalysisLogs(logFile,
                new String[]{
                        "[meterian] Client successfully authorized",
                        "[meterian] Meterian Client v",
                        "[meterian] - autofix mode:      on",
                        "[meterian] Running autofix, 1 programs",
                        "[meterian] Autofix applied, will run the build again.",
                        "[meterian] Project information:",
                        "[meterian] JAVA scan -",
                        "MeterianHQ/autofix-sample-maven-upgrade.git",
                        "[meterian] Execution successful!",
                        "[meterian] Final results:",
                        "[meterian] - security:\t35\t(minimum: 90)",
                        "[meterian] - stability:\t100\t(minimum: 80)",
                        "[meterian] - licensing:\t99\t(minimum: 95)",
                        "[meterian] Build unsuccesful!",
                        "[meterian] Failed checks: [security]",
                        "Meterian client analysis failed with exit code 1",
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
                getMeterianGithubUser(),
                getMeterianGithubEmail(),
                jenkinsLogger
        );
        assertThat("Should have had nothing to commit on current branch",
                gitClient.hasChanges(), is(false));
        assertThat("Should have been positioned at the current branch by the name 'partially-fixed-by-autofix'",
                gitClient.getCurrentBranch(),
                is(equalTo("partially-fixed-by-autofix")));
    }

    private void verifyRunAnalysisLogs(File logFile,
                                       String[] containsLogLines,
                                       String[] doesNotContainLogLines) throws IOException {
        String runAnalysisLogs = readRunAnalysisLogs(logFile.getPath());

        for (String eachLogLine: containsLogLines) {
            assertThat(runAnalysisLogs, containsString(eachLogLine));
        }

        for (String eachLogLine: doesNotContainLogLines) {
            assertThat(runAnalysisLogs, not(containsString(eachLogLine)));
        }
    }

    private void configureGitUserNameAndEmail(String userName, String userEmail) throws IOException {
        // git config --global user.name "Your Name"
        String[] gitConfigUserNameCommand = new String[] {
                "git",
                "config",
                "--local",
                "user.name",
                userName
        };

        int exitCode = runCommand(gitConfigUserNameCommand, gitRepoWorkingFolder, log);

        assertThat("Cannot run the test, as we were unable configure a user due to error code: " +
                exitCode, exitCode, is(equalTo(0)));

        // git config --global user.email "you@example.com"
        String[] gitConfigUserEmailCommand = new String[] {
                "git",
                "config",
                "--local",
                "user.email",
                userEmail
        };

        exitCode = runCommand(gitConfigUserEmailCommand, gitRepoWorkingFolder, log);

        assertThat("Cannot run the test, as we were unable configure a user userEmail due to error code: " +
                exitCode, exitCode, is(equalTo(0)));
    }

    private void deleteRemoteBranch(String branchName) throws IOException {
        String[] gitCloneRepoCommand = new String[] {
                "git",
                "push",
                "origin",
                ":" + branchName
        };

        int exitCode = runCommand(gitCloneRepoCommand, gitRepoWorkingFolder, log);

        assertThat("Cannot run the test, as we were unable to remove a remote branch from a repo due to error code: " +
                exitCode, exitCode, is(equalTo(0)));

    }

    private String readRunAnalysisLogs(String pathToLog) throws IOException {
        File logFile = new File(pathToLog);
        return FileUtils.readFileToString(logFile);
    }

    private void performCloneGitRepo(String githubOrgOrUserName, String githubProjectName, String workingFolder, String branch) throws IOException {
        String[] gitCloneRepoCommand = new String[] {
                "git",
                "clone",
                String.format("git@github.com:%s/%s.git", githubOrgOrUserName, githubProjectName), // only use ssh or git protocol and not https - uses ssh keys to authenticate
                "-b",
                branch
        };

        int exitCode = runCommand(gitCloneRepoCommand, workingFolder, log);

        assertThat("Cannot run the test, as we were unable to clone the target git repo due to error code: " +
                exitCode, exitCode, is(equalTo(0)));
    }


    private int runCommand(String[] command, String workingFolder, Logger log) throws IOException {
        LineGobbler errorLineGobbler = (type, text) ->
                log.error("{}> {}", type, text);

        Shell.Options options = new Shell.Options()
                .onDirectory(new File(workingFolder))
                .withErrorGobbler(errorLineGobbler)
                .withEnvironmentVariables(getOSEnvSettings());
        Shell.Task task = new Shell().exec(
                command,
                options
        );

        return task.waitFor();
    }

    private PrintStream nullPrintStream() {
        return new PrintStream(new NullOutputStream());
    }

    private static HttpClient newHttpClient() {
        return new HttpClientFactory().newHttpClient(new HttpClientFactory.Config() {
            @Override
            public int getHttpConnectTimeout() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getHttpSocketTimeout() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getHttpMaxTotalConnections() {
                return 100;
            }

            @Override
            public int getHttpMaxDefaultConnectionsPerRoute() {
                return 100;
            }

            @Override
            public String getHttpUserAgent() {
                // TODO Auto-generated method stub
                return null;
            }});
    }

    private String getMeterianGithubUser() {
        return getOSEnvSettings().get("METERIAN_GITHUB_USER");
    }

    private String getMeterianGithubEmail() {
        return getOSEnvSettings().get("METERIAN_GITHUB_EMAIL");
    }

    private EnvVars getEnvironment() {
        EnvVars environment = getOSEnvSettings();
        environment.put("WORKSPACE", gitRepoWorkingFolder);
        return environment;
    }

    private EnvVars getOSEnvSettings() {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars environment = prop.getEnvVars();

        Map<String, String> localEnvironment = new OS().getenv();
        for (String envKey: localEnvironment.keySet()) {
            environment.put(envKey, localEnvironment.get(envKey));
        }
        return environment;
    }
}