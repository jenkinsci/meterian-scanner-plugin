package io.meterian.test_management;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.client.HttpClient;
import org.eclipse.jgit.api.Git;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.*;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import static org.mockito.Mockito.*;
import org.slf4j.Logger;

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

public class TestManagement {

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String NO_JVM_ARGS = "";

    private String gitRepoWorkingFolder;
    private Logger log;
    private PrintStream jenkinsLogger;
    private EnvVars environment;
    private LocalGitClient gitClient;
    private CredentialsProvider credentialsProvider;

    public TestManagement(String gitRepoWorkingFolder,
                          Logger log,
                          PrintStream jenkinsLogger) {
        this.gitRepoWorkingFolder = gitRepoWorkingFolder;
        this.log = log;
        this.jenkinsLogger = jenkinsLogger;

        environment = getEnvironment();
    }

    public TestManagement() {}


    public void runMeterianClientAndReportAnalysis(MeterianPlugin.Configuration configuration, PrintStream jenkinsLogger) {
        try {
            File clientJar = getClientJar();
            Meterian client = getMeterianClient(configuration, clientJar);
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

    public void performCloneGitRepo(String githubOrgOrUserName, String githubProjectName, String workingFolder, String branch) {
        String repoURI = String.format(
                "git@github.com:%s/%s.git", githubOrgOrUserName, githubProjectName);
        try {
            Git.cloneRepository()
                    .setTransportConfigCallback(getTransportConfigCallback())
                    .setCredentialsProvider(credentialsProvider)
                    .setURI(repoURI)
                    .setBranch(branch)
                    .setDirectory(new File(workingFolder))
                    .call();
        } catch (Exception ex) {
            fail(String.format("Cannot run the test, as we were unable to clone the target git repo due to an error: %s (cause: %s)",
                    ex.getMessage(), ex.getCause()));
        }
    }

    public boolean branchExists(String branchName) throws GitAPIException {
        return ! getGitClient().findBranchByName(branchName).isEmpty();
    }

    public void resetBranch(String branch) throws GitAPIException {
        checkoutBranch(branch);
        getGitClient().resetChanges();
    }

    public void checkoutBranch(String branch) throws GitAPIException {
        String currentBranch = getGitClient().checkoutBranch(branch).getName();

        assertThat("Cannot run the test, as we were unable to switch to the target branch on the target repo",
                branch, is(equalTo(currentBranch
                        .replace("refs/heads/", ""))));
    }

    public void deleteRemoteBranch(String workingFolder, String branchName) {
        try {
            Git git = Git.open(new File(workingFolder));
            RefSpec refSpec = new RefSpec()
                    .setSource(null)
                    .setDestination("refs/heads/" + branchName);
            git.push()
                    .setTransportConfigCallback(getTransportConfigCallback())
                    .setCredentialsProvider(credentialsProvider)
                    .setRefSpecs(refSpec)
                    .setRemote("origin")
                    .call();
            log.info(String.format("Successfully removed remote branch %s from the repo", branchName));
        } catch (IOException | GitAPIException ex) {
            log.warn(
                    String.format("We were unable to remove a remote branch %s from the repo, " +
                            "maybe the branch does not exist or the name has changed", branchName)
            );
        }
    }

    private TransportConfigCallback getTransportConfigCallback() {
        return transport -> {
            SshTransport sshTransport = ( SshTransport )transport;
            sshTransport.setSshSessionFactory( new JschConfigSessionFactory() {
                @Override
                protected void configure(OpenSshConfig.Host host, Session session) {
                    session.setPassword(getMeterianGithubToken());
                }
            });
        };
    }

    public String getFixedByMeterianBranchName(String repoWorkspace, String branch) throws Exception {
        try {
            LocalGitClient gitClient = new LocalGitClient(
                    repoWorkspace,
                    getMeterianGithubUser(),
                    getMeterianGithubToken(),
                    getMeterianGithubEmail(),
                    jenkinsLogger
            );

            gitClient.checkoutBranch(branch);
            return String.format("fixed-by-meterian-%s", gitClient.getCurrentBranchSHA());
        } catch (Exception ex) {
            jenkinsLogger.println(String.format(
                    "Could not fetch the name of the fixed-by-meterian-xxxx branch, due to error: %s" , ex.getMessage())
            );
            throw new Exception(ex);
        }
    }

    public String getMeterianGithubUser() {
        return getOSEnvSettings().get("METERIAN_GITHUB_USER");
    }

    public String getMeterianGithubToken() {
        return getOSEnvSettings().get("METERIAN_GITHUB_TOKEN");
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

        String meterianGithubToken = environment.get("METERIAN_GITHUB_TOKEN");
        assertThat("METERIAN_GITHUB_TOKEN has not been set, cannot run test without a valid value", meterianGithubToken, notNullValue());

        String meterianGithubUser = getMeterianGithubUser();
        if ((meterianGithubUser == null) || meterianGithubUser.trim().isEmpty()) {
            jenkinsLogger.println("METERIAN_GITHUB_USER has not been set, tests will be run using the default value assumed for this environment variable");
        }

        String meterianGithubEmail = getMeterianGithubEmail();
        if ((meterianGithubEmail == null) || meterianGithubEmail.trim().isEmpty()) {
            jenkinsLogger.println("METERIAN_GITHUB_EMAIL has not been set, tests will be run using the default value assumed for this environment variable");
        }

        // See https://www.codeaffine.com/2014/12/09/jgit-authentication/ for explanation on why this is allowed
        // First argument of UsernamePasswordCredentialsProvider is the token, the second argument is empty/blank
        credentialsProvider = new UsernamePasswordCredentialsProvider(meterianGithubToken, "");

        MeterianPlugin.Configuration standardConfiguration = new MeterianPlugin.Configuration(
                BASE_URL,
                "",
                NO_JVM_ARGS,
                meterianGithubUser,
                meterianGithubEmail,
                ""
        );

        MeterianPlugin.Configuration configuration = spy(standardConfiguration);
        when(configuration.getMeterianAPIToken()).thenReturn(meterianAPIToken);
        when(configuration.getMeterianGithubToken()).thenReturn(meterianGithubToken);

        return configuration;
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

    public HttpClient newHttpClient() {
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

    private LocalGitClient getGitClient() {
        if (gitClient == null) {
            gitClient = new LocalGitClient(
                    gitRepoWorkingFolder,
                    getMeterianGithubUser(),
                    getMeterianGithubToken(),
                    getMeterianGithubEmail(),
                    jenkinsLogger
            );
        }

        return gitClient;
    }
}
