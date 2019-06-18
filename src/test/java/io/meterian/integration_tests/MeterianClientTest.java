package io.meterian.integration_tests;

import io.meterian.test_management.TestManagement;
import org.apache.commons.io.FileUtils;

import org.junit.Before;
import org.junit.Test;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import hudson.EnvVars;

import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.MeterianPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeterianClientTest {

    private static final Logger log = LoggerFactory.getLogger(MeterianClientTest.class);

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");

    private static final String NO_JVM_ARGS = "";

    private TestManagement testManagement;
    private File logFile;
    private PrintStream jenkinsLogger;

    private String githubProjectName = "autofix-sample-maven-upgrade";
    private String gitRepoRootFolder = Paths.get(CURRENT_WORKING_DIR, "target/github-repo/").toString();
    private String gitRepoWorkingFolder = Paths.get(gitRepoRootFolder, githubProjectName).toString();

    private EnvVars environment;

    @Before
    public void setup() throws IOException {
        logFile = File.createTempFile("jenkins-logger-", Long.toString(System.nanoTime()));
        jenkinsLogger = new PrintStream(logFile);
        log.info("Jenkins log file: " + logFile.toPath().toString());

        testManagement = new TestManagement(gitRepoWorkingFolder, log, jenkinsLogger);
        environment = testManagement.getEnvironment();

        String gitRepoRootFolder = Paths.get(CURRENT_WORKING_DIR, "target/github-repo/").toString();
        FileUtils.deleteDirectory(new File(gitRepoRootFolder));

        new File(gitRepoRootFolder).mkdir();

        //gitRepoWorkingFolder =
        testManagement.performCloneGitRepo(
                "MeterianHQ",
                "autofix-sample-maven-upgrade",
                gitRepoRootFolder,
                "master");
    }

    @Test
    public void givenConfiguration_whenMeterianClientIsRun_thenItShouldNotThrowException() throws IOException {
        File logFile = File.createTempFile("jenkins-logger", Long.toString(System.nanoTime()));
        PrintStream jenkinsLogger = new PrintStream(logFile);

        // Given: we are setup to run the meterian client against a repo that has vulnerabilities
        String meterianAPIToken = environment.get("METERIAN_API_TOKEN");
        assertThat("METERIAN_API_TOKEN has not been set, cannot run test without a valid value", meterianAPIToken, notNullValue());
        String meterianGithubToken = environment.get("METERIAN_GITHUB_TOKEN");
        assertThat("METERIAN_GITHUB_TOKEN has not been set, cannot run test without a valid value", meterianGithubToken, notNullValue());

        String meterianGithubUser = testManagement.getMeterianGithubUser();
        if ((meterianGithubUser == null) || meterianGithubUser.trim().isEmpty()) {
            jenkinsLogger.println("METERIAN_GITHUB_USER has not been set, tests will be run using the default value assumed for this environment variable");
        }

        String meterianGithubEmail = testManagement.getMeterianGithubEmail();
        if ((meterianGithubEmail == null) || meterianGithubEmail.trim().isEmpty()) {
            jenkinsLogger.println("METERIAN_GITHUB_EMAIL has not been set, tests will be run using the default value assumed for this environment variable");
        }

        MeterianPlugin.Configuration configuration = new MeterianPlugin.Configuration(
                BASE_URL,
                meterianAPIToken,
                NO_JVM_ARGS,
                meterianGithubUser,
                meterianGithubEmail,
                meterianGithubToken
        );

        String args = "";

        // When: the meterian client is run against the locally cloned git repo
        try {
            File clientJar = testManagement.getClientJar();
            Meterian client = testManagement.getMeterianClient(configuration, clientJar);
            client.prepare("--interactive=false");
            client.run();
            jenkinsLogger.close();
        } catch (Exception ex) {
            fail("Should not have failed with the exception: " + ex.getMessage());
        }

        // Then: we should be able to see the expecting output in the execution analysis output logs
        testManagement.verifyRunAnalysisLogs(logFile,
                new String[]{},
                new String[]{
                        "[meterian] Client successfully authorized",
                        "[meterian] Meterian Client v",
                        "[meterian] Project information:",
                        "[meterian] JAVA scan -",
                        "MeterianHQ/autofix-sample-maven-upgrade.git",
                        "[meterian] Full report available at: ",
                        "[meterian] Build unsuccesful!",
                        "[meterian] Failed checks: [security]"
                }
        );
    }
}