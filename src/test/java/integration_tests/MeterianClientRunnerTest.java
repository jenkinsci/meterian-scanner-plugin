package integration_tests;

import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.MeterianPlugin;
import io.meterian.jenkins.io.ClientDownloader;
import io.meterian.jenkins.io.HttpClientFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.client.HttpClient;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class MeterianClientRunnerTest {

    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");
    private static final String BASE_URL = "https://www.meterian.com";

    private String jenkinsProjectName = "TestMeterianPlugin-Freestyle-autofix";

    @Test
//    @Ignore("Runs only in an environment with the Jenkins hpi and Meterian setup in place")
    public void givenConfiguration_whenMeterianClientIsRun_thenItShouldNotThrowException()
            throws IOException, ParserConfigurationException, SAXException {
        EnvVars environment = getEnvironment(jenkinsProjectName);
        MeterianPlugin.Configuration configuration = new MeterianPlugin.Configuration(
                BASE_URL,
                environment.get("METERIAN_API_TOKEN"),
                "", // jvmArgs
                environment.get("METERIAN_GITHUB_TOKEN")
        );

        File logFile = File.createTempFile("jenkins-logger", Long.toString(System.nanoTime()));
        PrintStream jenkinsLogger = new PrintStream(logFile);

        String args = "";

        try {
            File clientJar = new ClientDownloader(newHttpClient(), BASE_URL, nullPrintStream()).load();
            Meterian client = Meterian.build(configuration, environment, jenkinsLogger, args, clientJar);
            client.prepare("--interactive=false");
            client.run();
            jenkinsLogger.close();

            String buildLogs = readBuildLogs(logFile.getPath());
            assertThat(buildLogs, containsString("[meterian] Client successfully authorized"));
            assertThat(buildLogs, containsString("[meterian] Meterian Client v"));
            assertThat(buildLogs, containsString("[meterian] Project information:"));
            assertThat(buildLogs, containsString("[meterian] JAVA scan -"));
        } catch (Exception ex) {
            fail("Should not have failed with the exception: " + ex.getMessage());
        }
    }

    private String readBuildLogs(String pathToBuildLog) throws IOException {
        File buildLogFile = new File(pathToBuildLog);
        return FileUtils.readFileToString(buildLogFile);
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

    private EnvVars getEnvironment(String projectName) throws IOException, SAXException, ParserConfigurationException {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars environment = prop.getEnvVars();
        environment.put("WORKSPACE", getPathToWorkspace(projectName));

        String pathToMeterianPluginConfig = CURRENT_WORKING_DIR + "/work/io.meterian.jenkins.glue.MeterianPlugin.xml";
        Document xmlDocument = loadXMLFile(pathToMeterianPluginConfig);
        environment.put("METERIAN_API_TOKEN", xmlDocument.getElementsByTagName("token").item(0).getTextContent());
        environment.put("METERIAN_GITHUB_TOKEN", xmlDocument.getElementsByTagName("githubToken").item(0).getTextContent());
        return environment;
    }

    private Document loadXMLFile(String filename) throws ParserConfigurationException, IOException, SAXException {
        File file = new File(filename);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(file);
        document.getDocumentElement().normalize();
        return document;
    }

    private String getPathToWorkspace(String projectName) {
        return String.format("%s/work/workspace/%s", CURRENT_WORKING_DIR, projectName);
    }
}