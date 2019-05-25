package integration_tests;

import com.meterian.common.system.OS;
import com.meterian.common.system.Shell;
import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import io.meterian.jenkins.autofixfeature.AutoFixFeature;
import io.meterian.jenkins.autofixfeature.git.LocalGitClient;
import io.meterian.jenkins.autofixfeature.github.LocalGitHubClient;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.MeterianPlugin;
import io.meterian.jenkins.glue.clientrunners.MultiStageClientRunner;
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
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class MeterianClientAutofixFeatureTest {

    private static final String BASE_URL = "https://www.meterian.com";
    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");

    private static final String NO_JVM_ARGS = "";

    private String gitRepoWorkingFolder;

    @Before
    public void setup() throws IOException {
        String gitRepoRootFolder = Paths.get(CURRENT_WORKING_DIR, "target/github-repo/").toString();
        FileUtils.deleteDirectory(new File(gitRepoRootFolder));

        new File(gitRepoRootFolder).mkdir();

        gitRepoWorkingFolder = performCloneGitRepo(gitRepoRootFolder);
    }

    @Test
    public void givenConfiguration_whenMeterianClientIsRunWithAutofixOption_thenItShouldReturnAnalysisReportAndFixThem() throws IOException, GitAPIException {
        // Given: we are setup to run the meterian client against a repo that has vulnerabilities

        EnvVars environment = getEnvironment();
        String meterianAPIToken = environment.get("METERIAN_API_TOKEN");
        assertThat("METERIAN_API_TOKEN has not been set, cannot run test without a valid value", meterianAPIToken, notNullValue());
        String meterianGithubToken = environment.get("METERIAN_GITHUB_TOKEN");
        assertThat("METERIAN_GITHUB_TOKEN has not been set, cannot run test without a valid value", meterianGithubToken, notNullValue());

        MeterianPlugin.Configuration configuration = new MeterianPlugin.Configuration(
                BASE_URL,
                meterianAPIToken,
                NO_JVM_ARGS,
                meterianGithubToken
        );

        File logFile = File.createTempFile("jenkins-logger", Long.toString(System.nanoTime()));
        PrintStream jenkinsLogger = new PrintStream(logFile);

        String args = "";

        // When: the meterian client is run against the locally cloned git repo with the autofix feature (--autofix) passed as a CLI arg
        try {
            File clientJar = new ClientDownloader(newHttpClient(), BASE_URL, nullPrintStream()).load();
            Meterian client = Meterian.build(configuration, environment, jenkinsLogger, args, clientJar);

            MultiStageClientRunner clientRunner =
                    new MultiStageClientRunner(client, mock(StepContext.class), jenkinsLogger);

            AutoFixFeature autoFixFeature = new AutoFixFeature(
                    configuration,
                    environment.get("WORKSPACE"),
                    clientRunner,
                    jenkinsLogger
            );
            MeterianExecutor executor = new StandardExecutor(mock(StepContext.class), clientRunner, autoFixFeature);

            jenkinsLogger.close();


        } catch (Exception ex) {
            fail("Should not have failed with the exception: " + ex.getMessage());
        }

        // Then: we should be able to see the expected output in the execution analysis output logs and the
        // reported vulnerabilities should be fixed, the changes committed to a branch and a pull request
        // created onto the respective remote Github repository of the project
        LocalGitClient gitClient = mock(LocalGitClient.class);
        verify(gitClient).applyCommitsToLocalRepo();
        verify(gitClient).pushBranchToRemoteRepo();

        LocalGitHubClient localGitHubClient = mock(LocalGitHubClient.class);
        verify(localGitHubClient).createPullRequest(anyString());
    }

    private String performCloneGitRepo(String gitRepoRootFolder) throws IOException {
        String githubProjectName = "autofix-sample-maven-upgrade";
        String[] gitCloneRepoCommand = new String[] {
                "git",
                "clone",
                "https://github.com/MeterianHQ/" + githubProjectName + ".git"
        };

        Shell.Options options = new Shell.Options().
                onDirectory(new File(gitRepoRootFolder));
        Shell.Task task = new Shell().exec(
                gitCloneRepoCommand,
                options
        );
        task.waitFor();

        assertThat("Cannot run the test, as we were unable to clone the target git repo due to error code: " +
                task.exitValue(), task.exitValue(), is(equalTo(0)));

        return Paths.get(gitRepoRootFolder, githubProjectName).toString();
    }

    private String readRunAnalysisLogs(String pathToLog) throws IOException {
        File logFile = new File(pathToLog);
        return FileUtils.readFileToString(logFile);
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
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars environment = prop.getEnvVars();

        Map<String, String> localEnvironment = new OS().getenv();
        for (String envKey: localEnvironment.keySet()) {
            environment.put(envKey, localEnvironment.get(envKey));
        }
        environment.put("WORKSPACE", gitRepoWorkingFolder);
        return environment;
    }
}