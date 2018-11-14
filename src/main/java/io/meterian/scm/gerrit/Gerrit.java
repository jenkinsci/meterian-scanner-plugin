package io.meterian.scm.gerrit;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension;

import hudson.EnvVars;
import hudson.model.Run;
import io.meterian.jenkins.glue.MeterianPlugin;

public class Gerrit {

    private static final Logger log = LoggerFactory.getLogger(MeterianPlugin.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final EnvVars environment;
    private final PrintStream logger;
    private final Run<?, ?> run;

    private GerritApi api;

    public static Gerrit build(EnvVars envVars, Run<?, ?> run, PrintStream logger) {
        try {
            return new Gerrit(envVars, run, logger).init();
        } catch (Exception ex) {
            log.warn("Unable to create Gerrit instance", ex);
            return null;
        }
    }

    private Gerrit(EnvVars envVars, Run<?, ?> run, PrintStream logger)
            throws URISyntaxException, IOException, RestApiException {
        this.environment = envVars;
        this.run = run;
        this.logger = logger;
    }

    private Gerrit init() throws URISyntaxException, IOException, RestApiException {
        URIish gerritApiUrl = getGerritUrl(environment);
        if (gerritApiUrl == null)
            throw new IOException("No GERRIT url found!");

//      insecureHttps(Boolean.parseBoolean(envVars.get("GERRIT_API_INSECURE_HTTPS")));

        StandardUsernamePasswordCredentials credentials = getCredentials();
        String username = credentials.getUsername();
        String password = credentials.getPassword().getPlainText();

        this.api = getGerritApi(gerritApiUrl, username, password);
        logger.println("[meterian] Gerrit remote version: " + api.config().server().getVersion());

        return this;
    }

    public Map<String, FileInfo> getChangedFiles() throws IOException {
        GerritChange change = getCurrentChange();
        if (change == null)
            return Collections.emptyMap();
        
        log.debug("Calling APIs...");
        try {
            Map<String, FileInfo> files = api.changes()
                    .id(change.getChangeId())
                    .revision(change.getRevision())
                    .files();
            log.debug("Changes: {}", files);
            return files;
        } catch (Exception ex) {
            log.warn("API call error", ex);
            throw new IOException("API call error", ex);
        }
    }

    private GerritChange getCurrentChange() throws IOException {
        try {
            GerritChange change = new GerritChange(environment, logger);
            if (change.getChangeId() != null) {
                logger.format("[meterian] Gerrit change %d/%d %n", change.getChangeId(), change.getRevision());
                return change;
            } else {
                logger.println("[meterian] No changes detected");
                return null;
            }
        } catch (InterruptedException ex) {
            log.warn("Unexpected", ex);
            Thread.currentThread().interrupt();
            throw new IOException("Commnuication interrupted!");
        }
    }

    public void apply(List<GerritRoboComment> comments) throws IOException {
        try {
            doApply(comments);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void doApply(List<GerritRoboComment> comments) throws Exception {
        logger.format("[meterian] %d possible insights found for this change %n", comments.size());
        log.info("Comments: {}", gson.toJson(comments));

        ReviewInput input = ReviewInput.dislike();
        input.drafts = ReviewInput.DraftHandling.PUBLISH;
        input.tag = "autogenerated:jenkins";
        input.message = "Meterian disagrees with your choice of libraries :)";

        input.robotComments = new HashMap<>();
        for (GerritRoboComment comment : comments) {
            input.robotComments.put(comment.filename(), comment.asRobotCommentInput());
        }
        log.info("Generated comments: {}", gson.toJson(input.robotComments));


        log.info("Calling gerit APIs...");
        GerritChange change = new GerritChange(environment, logger);
        ReviewResult res = api.changes()
                .id(change.getChangeId())
                .revision(change.getRevision())
                .review(input);
        log.info("API called succesfully: {}", gson.toJson(res));
        logger.format("[meterian] Robot comment(s) successfully applied! %n", input.robotComments.size());
    }


    private GerritApi getGerritApi(URIish gerritApiUrl, String username, String password) {
        List<HttpClientBuilderExtension> extensions = new ArrayList<>();

        GerritAuthData authData = null;
        if (gerritApiUrl == null) {
            logger.println("Gerrit Review is disabled no API URL");
        } else if (username == null) {
            logger.println("Gerrit Review is disabled no credentials");
            authData = new AnonymousAuth(gerritApiUrl.toString());
        } else {
            authData = new GerritAuthData.Basic(gerritApiUrl.toString(), username, password);
//            if (Boolean.TRUE.equals(insecureHttps)) {
//                extensions.add(SSLNoVerifyCertificateManagerClientBuilderExtension.INSTANCE);
//            }
        }

        return new GerritRestApiFactory().create(authData, extensions.toArray(new HttpClientBuilderExtension[0]));
    }

    private StandardUsernamePasswordCredentials getCredentials() {
        String credentialsId = environment.get("GERRIT_CREDENTIALS_ID");
        StandardUsernamePasswordCredentials credentials = CredentialsProvider.findCredentialById(
                credentialsId,
                StandardUsernamePasswordCredentials.class,
                run);

        return credentials;
    }
    
    private static URIish getGerritUrl(EnvVars environment) throws URISyntaxException, IOException {
        if (environment.containsKey("GERRIT_API_URL")) {
            return new URIish(environment.get("GERRIT_API_URL"));
        } else if (environment.containsKey("GERRIT_CHANGE_URL")) {
            return new GerritURI(new URIish(environment.get("GERRIT_CHANGE_URL"))).getApiURI();
        } else
            return null;
    }

    public static boolean isSupported(EnvVars environment) {
        try {
            return getGerritUrl(environment) != null;
        } catch (Exception any) {
            log.warn("Unexpected", any);
            return false;
        }
    }

    public static class AnonymousAuth implements GerritAuthData {
        private final String gerritApiUrl;

        public AnonymousAuth(String gerritApiUrl) {
            this.gerritApiUrl = gerritApiUrl;
        }

        @Override
        public String getLogin() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean isHttpPassword() {
            return false;
        }

        @Override
        public String getHost() {
            return gerritApiUrl;
        }

        @Override
        public boolean isLoginAndPasswordAvailable() {
            return false;
        }
    }

}
