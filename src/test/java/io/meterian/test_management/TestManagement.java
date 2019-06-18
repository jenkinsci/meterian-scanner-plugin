package io.meterian.test_management;

import com.meterian.common.system.LineGobbler;
import com.meterian.common.system.OS;
import com.meterian.common.system.Shell;
import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.MeterianPlugin;
import io.meterian.jenkins.io.ClientDownloader;
import io.meterian.jenkins.io.HttpClientFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class TestManagement {

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String NO_JVM_ARGS = "";

    private String gitRepoWorkingFolder;
    private Logger log;
    private PrintStream jenkinsLogger;
    private EnvVars environment;

    public TestManagement(String gitRepoWorkingFolder,
                          Logger log,
                          PrintStream jenkinsLogger) {
        this.gitRepoWorkingFolder = gitRepoWorkingFolder;
        this.log = log;
        this.jenkinsLogger = jenkinsLogger;

        environment = getEnvironment();
    }

    public TestManagement() {}

    public void verifyRunAnalysisLogs(File logFile,
                                      String[] containsLogLines,
                                      String[] doesNotContainLogLines) throws IOException {
        String runAnalysisLogs = FileUtils.readFileToString(new File(logFile.getPath()));

        for (String eachLogLine: containsLogLines) {
            assertThat(runAnalysisLogs, containsString(eachLogLine));
        }

        for (String eachLogLine: doesNotContainLogLines) {
            assertThat(runAnalysisLogs, not(containsString(eachLogLine)));
        }
    }

    public void configureGitUserNameAndEmail(String userName, String userEmail) throws IOException {
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

    public void deleteRemoteBranch(String branchName) throws IOException {
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

    public String performCloneGitRepo(String githubOrgOrUserName, String githubProjectName, String workingFolder, String branch) throws IOException {
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

        return Paths.get(workingFolder, githubProjectName).toString();
    }

    public String getMeterianGithubUser() {
        return getOSEnvSettings().get("METERIAN_GITHUB_USER");
    }

    public String getMeterianGithubEmail() {
        return getOSEnvSettings().get("METERIAN_GITHUB_EMAIL");
    }

    public EnvVars getEnvironment() {
        EnvVars environment = getOSEnvSettings();
        environment.put("WORKSPACE", gitRepoWorkingFolder);
        return environment;
    }

    public MeterianPlugin.Configuration getConfiguration() {
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

        return new MeterianPlugin.Configuration(
                BASE_URL,
                meterianAPIToken,
                NO_JVM_ARGS,
                meterianGithubUser,
                meterianGithubEmail,
                meterianGithubToken
        );
    }

    public File getClientJar() throws IOException {
        return new ClientDownloader(newHttpClient(), BASE_URL, nullPrintStream()).load();
    }

    public Meterian getMeterianClient(MeterianPlugin.Configuration configuration, File clientJar) throws IOException {
        return Meterian.build(configuration, environment, jenkinsLogger, NO_JVM_ARGS, clientJar);
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

    public PrintStream nullPrintStream() {
        return new PrintStream(new NullOutputStream());
    }

    public static HttpClient newHttpClient() {
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
}
