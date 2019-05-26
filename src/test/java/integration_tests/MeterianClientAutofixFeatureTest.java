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
    public void setup() {
        environment = getEnvironment();
        String meterianAPIToken = environment.get("METERIAN_API_TOKEN");
        assertThat("METERIAN_API_TOKEN has not been set, cannot run test without a valid value", meterianAPIToken, notNullValue());
        String meterianGithubToken = environment.get("METERIAN_GITHUB_TOKEN");
        assertThat("METERIAN_GITHUB_TOKEN has not been set, cannot run test without a valid value", meterianGithubToken, notNullValue());

        configuration = new MeterianPlugin.Configuration(
                BASE_URL,
                meterianAPIToken,
                NO_JVM_ARGS,
                meterianGithubToken
        );
    }

    @Test
    public void step1_givenConfiguration_whenMeterianClientIsRunWithAutofixOptionForTheFirstTime_thenItShouldReturnAnalysisReportAndFixThem() throws IOException {
        // Given: we are setup to run the meterian client against a repo that has vulnerabilities
        FileUtils.deleteDirectory(new File(gitRepoRootFolder));
        new File(gitRepoRootFolder).mkdir();
        performCloneGitRepo("MeterianHQ", githubProjectName, gitRepoRootFolder);

        // Deleting remote branch automatically closes any Pull Request attached to it
        configureGitUserNameAndEmail(LocalGitClient.METERIAN_BOT, LocalGitClient.METERIAN_BOT_EMAIL);
        deleteRemoteBranch("fixed-by-meterian-29c4d26");

        createLoggers();

        // When: the meterian client is run against the locally cloned git repo with the autofix feature (--autofix) passed as a CLI arg
        runMeterianClientAndReportAnalysis(jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and the
        // reported vulnerabilities should be fixed, the changes committed to a branch and a pull request
        // created onto the respective remote Github repository of the project
        verifyRunAnalysisLogs(logFile, new String[]{
                "[meterian] Client successfully authorized",
                "[meterian] Meterian Client v",
                "[meterian] - autofix mode:      on",
                "[meterian] Running autofix, 1 programs",
                "[meterian] Autofix applied, will run the build again.",
                "[meterian] Project information:",
                "[meterian] JAVA scan -",
                "MeterianHQ/autofix-sample-maven-upgrade.git",
                "[meterian] Full report available at: ",
                "[meterian] Build unsuccesful!",
                "[meterian] Failed checks: [security]",
                "[meterian] Finished creating pull request for org: MeterianHQ, repo: MeterianHQ/autofix-sample-maven-upgrade, branch: fixed-by-meterian-29c4d26."
        });
    }

    @Test
    public void step2_givenMeterianClientHasBeenRunAsInStep1_whenMeterianClientIsReRunWithAutofixOptionOnAFixedBranch_thenItShouldDoNothing() throws IOException {
        // Given: the Meterian Client has been run once before (in step 1 of the test)
        // a local branch called fixed-by-meterian-29c4d26 already exists
        // a remote branch called fixed-by-meterian-29c4d26 already exists
        // a pull request attached to remote branch fixed-by-meterian-29c4d26 also exists
        // and the current branch is fixed-by-meterian-29c4d26
        createLoggers();

        // When: meterian client is run on the current branch (which has already been fixed before)
        runMeterianClientAndReportAnalysis(jenkinsLogger);

        // Then: we should be able to see the expected output in the execution analysis output logs and no action should
        // be taken by it, it should report warnings of the presence of the local branch with the fixes, the remote branch
        // with the fixes and also the pull request attached to this remote branch
        verifyRunAnalysisLogs(logFile, new String[]{
                "[meterian] Client successfully authorized",
                "[meterian] Meterian Client v",
                "[meterian] - autofix mode:      on",
                "[meterian] Running autofix, 1 programs",
                "[meterian] Sorry, no changes made. Try a different reach or strategy!",
                "[meterian] Project information:",
                "[meterian] JAVA scan -",
                "MeterianHQ/autofix-sample-maven-upgrade.git",
                "[meterian] Full report available at: ",
                "[meterian] Build unsuccesful!",
                "[meterian] Failed checks: [security]",
                "No changes found (no fixed generated), no branch to push to the remote repo.",
                "[meterian] Warning: fixed-by-meterian-29c4d26 already exists in the remote repo, skipping the remote branch creation process.",
                "[meterian] Warning: Found 1 pull request(s) for org: MeterianHQ, repo: MeterianHQ/autofix-sample-maven-upgrade, branch: fixed-by-meterian-29c4d26",
                "[meterian] Warning: Pull request already exists for this branch, no new pull request will be created. Fixed already generated for current branch (commit point)."
        });
    }

    private void createLoggers() throws IOException {
        logFile = File.createTempFile("jenkins-logger-", Long.toString(System.nanoTime()));
        jenkinsLogger = new PrintStream(logFile);
        log.info("Jenkins log file: " + logFile.toPath().toString());
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
                    environment.get("WORKSPACE"),
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

    private void verifyRunAnalysisLogs(File logFile, String[] specificLogLines) throws IOException {
        String runAnalysisLogs = readRunAnalysisLogs(logFile.getPath());
        for (String eachLogLine: specificLogLines) {
            assertThat(runAnalysisLogs, containsString(eachLogLine));
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

    private void performCloneGitRepo(String githubOrgOrUserName, String githubProjectName, String workingFolder) throws IOException {
        String[] gitCloneRepoCommand = new String[] {
                "git",
                "clone",
                "git@github.com:" + githubOrgOrUserName + "/" + githubProjectName + ".git"
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