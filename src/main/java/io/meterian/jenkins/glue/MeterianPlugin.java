package io.meterian.jenkins.glue;

import static io.meterian.jenkins.glue.Facade.getConfiguration;
import static io.meterian.jenkins.io.HttpClientFactory.makeUrl;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;

import javax.servlet.ServletException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import io.meterian.jenkins.autofixfeature.AutoFixFeature;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.clientrunners.ClientRunner;
import io.meterian.jenkins.glue.executors.StandardExecutor;
import io.meterian.jenkins.io.HttpClientFactory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;


@SuppressWarnings("rawtypes")
public class MeterianPlugin extends Builder {

    private static final String DEFAULT_METERIAN_GITHUB_USER = "meterian-bot";            // Machine User name, when user does not set one
    private static final String DEFAULT_METERIAN_GITHUB_EMAIL = "bot.github@meterian.io"; // Email associated with the Machine User, when user does not set one

    static final Logger log = LoggerFactory.getLogger(MeterianPlugin.class);

    private final String args;

    @DataBoundConstructor
    public MeterianPlugin(String args) {
        super();
        this.args = args;
    }

    public String getArgs() {
        return args;
    }


    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {

        EnvVars environment = build.getEnvironment(listener);
        Configuration configuration = getConfiguration();
        PrintStream jenkinsLogger = listener.getLogger();

        Meterian client = Meterian.build(
                configuration,
                environment,
                jenkinsLogger,
                args);

        if (! client.requiredEnvironmentVariableHasBeenSet()) {
            return false;
        }

        client.prepare("--interactive=false");

        ClientRunner clientRunner = new ClientRunner(client, build, jenkinsLogger);
        AutoFixFeature autoFixFeature = new AutoFixFeature(
                configuration,
                environment,
                clientRunner,
                jenkinsLogger
        );
        try {
            new StandardExecutor(clientRunner, autoFixFeature).run(client);
        } catch (Exception ex) {
            log.warn("Unexpected", ex);
            jenkinsLogger.println("Unexpected exception!");
            ex.printStackTrace(jenkinsLogger);
        }

        return true;
    }

    @Extension
    static public class Configuration extends BuildStepDescriptor<Builder> implements HttpClientFactory.Config {

        private static final String DEFAULT_BASE_URL = "https://www.meterian.io";
        private static final int ONE_MINUTE = 60 * 1000;

        private String url;
        private String meterianAPIToken;
        private String jvmArgs;

        private String meterianGithubUser;
        private String meterianGithubEmail;
        private String meterianGithubToken;

        public Configuration() {
            load();
        }

        public Configuration(String url,
                             String meterianAPIToken,
                             String jvmArgs,
                             String meterianGithubUser,
                             String meterianGithubEmail,
                             String meterianGithubToken) {
            this.url = url;
            this.meterianAPIToken = meterianAPIToken;
            this.jvmArgs = jvmArgs;
            this.meterianGithubUser = meterianGithubUser;
            this.meterianGithubEmail = meterianGithubEmail;
            this.meterianGithubToken = meterianGithubToken;

            log.info("Read configuration \nurl: [{}]\njvm: [{}]\nmeterianAPIToken: [{}]\nmeterianGithubUser: [{}]\nmeterianGithubEmail: [{}]\nmeterianGithubToken: [{}]",
                    url, jvmArgs, mask(meterianAPIToken),
                    meterianGithubUser == null ? getMeterianGithubUser() + " (default]" : meterianGithubUser,
                    meterianGithubEmail == null ? getMeterianGithubEmail() + " ([default)" : meterianGithubEmail,
                    mask(meterianGithubToken));
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true | FreeStyleProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "Executes Meterian analysis";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            
            Facade.checkPermission(Jenkins.ADMINISTER);

            url = computeFinalUrl(formData.getString("url"));
            meterianAPIToken = computeFinalToken(formData.getString("meterianAPIToken"));
            jvmArgs = parseEmpty(formData.getString("jvmArgs"), "");
            meterianGithubUser = parseEmpty(formData.getString("meterianGithubUser"), "");
            meterianGithubEmail = parseEmpty(formData.getString("meterianGithubEmail"), "");
            meterianGithubToken = parseEmpty(formData.getString("meterianGithubToken"), "");

            save();
            log.info("Stored configuration \nurl: [{}]\njvm: [{}]\nmeterianAPIToken: [{}]\nmeterianGithubUser: [{}]\nmeterianGithubEmail: [{}]\nmeterianGithubToken: [{}]",
                    url, jvmArgs, mask(meterianAPIToken),
                    meterianGithubUser == null ? getMeterianGithubUser() + " (default]" : meterianGithubUser,
                    meterianGithubEmail == null ? getMeterianGithubEmail() + " ([default)" : meterianGithubEmail,
                    mask(meterianGithubToken)
            );

            return super.configure(req, formData);
        }

        private String mask(String data) {
            if (data == null)
                return null;
            else
                return data.substring(0, Math.min(4, data.length() / 5)) + "...";
        }

        public String getUrl() {
            return url;
        }

        public String getJvmArgs() {
            return jvmArgs;
        }

        public String getMeterianAPIToken() {
            return meterianAPIToken;
        }


        public String getMeterianGithubUser() {
            if ((meterianGithubUser == null) || meterianGithubUser.trim().isEmpty()) {
                return DEFAULT_METERIAN_GITHUB_USER;
            }
            return meterianGithubUser;
        }

        public String getMeterianGithubEmail() {
            if ((meterianGithubEmail == null) || meterianGithubEmail.trim().isEmpty()) {
                return DEFAULT_METERIAN_GITHUB_EMAIL;
            }
            return meterianGithubEmail;
        }

        public String getMeterianGithubToken() {
            return meterianGithubToken;
        }

        public String getMeterianBaseUrl() {
            return parseEmpty(url, DEFAULT_BASE_URL);
        }

        public FormValidation doTestConnection(
                @QueryParameter("url") String testUrl,
                @QueryParameter("meterianAPIToken") String testToken
        ) throws IOException, ServletException {

            Facade.checkPermission(Jenkins.ADMINISTER);
            
            String apiUrl = computeFinalUrl(testUrl);
            String apiToken = computeFinalToken(testToken);
            log.info("The url to verify is [{}], the token is [{}]", apiUrl, apiToken);

            try {
                HttpClient client = new HttpClientFactory().newHttpClient(this);
                HttpGet request = new HttpGet(new URI(makeUrl(apiUrl, "/api/v1/accounts/me")));
                if (apiToken != null) {
                    String auth = "Token " + apiToken;
                    log.info("Using auth [{}]", auth);
                    request.addHeader("Authorization", auth);
                }

                HttpResponse response = client.execute(request);
                log.info("{}: {}", apiUrl, response);
                if (response.getStatusLine().getStatusCode() == 200) {
                    return FormValidation.ok("Success - connection to the Meterian API verified.");
                } else {
                    return FormValidation.error("Failed - status: " + response.getStatusLine());
                }
            } catch (Exception e) {
                log.error("Unexpected", e);
                return FormValidation.error("Error: " + e.getMessage());
            }
        }

        private String computeFinalToken(String testToken) {
            String apiToken = parseEmpty(testToken, null);
            return apiToken;
        }

        private String computeFinalUrl(String testUrl) {
            String apiUrl = parseEmpty(testUrl, DEFAULT_BASE_URL);
            return apiUrl;
        }

        private String parseEmpty(String text, String defval) {
            return (text == null || text.trim().isEmpty()) ? defval : text;
        }

        @Override
        public int getHttpConnectTimeout() {
            return ONE_MINUTE;
        }

        @Override
        public int getHttpSocketTimeout() {
            return ONE_MINUTE;
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
            return "meterian-jenkins_1.0";
        }
    }
}