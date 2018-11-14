package io.meterian.jenkins.core;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.meterian.common.system.LineGobbler;
import com.meterian.common.system.Shell;
import com.meterian.common.system.Shell.Options;
import com.meterian.common.system.Shell.Task;

import hudson.EnvVars;
import io.meterian.jenkins.glue.MeterianPlugin.Configuration;
import io.meterian.jenkins.io.ClientDownloader;
import io.meterian.jenkins.io.HttpClientFactory;

public class Meterian {

    public class Result {
        public int exitCode;
        public UUID projectUUID;
        public String projectBranch;
        public URI reportUrl;

        @Override
        public String toString() {
            return "[exitCode=" + exitCode + ", projectUUID=" + projectUUID + ", projectBranch=" + projectBranch + ", reportUrl=" + reportUrl + "]";
        }

    }

    private static final Logger log = LoggerFactory.getLogger(Meterian.class);

    private final Configuration config;
    private final EnvVars environment;
    private final PrintStream console;
    private final String args;
    private final Shell shell;

    private File clientJar;

    public static Meterian build(Configuration config, EnvVars environment, PrintStream logger, String args)
            throws IOException {
        Meterian meterian = new Meterian(config, environment, logger, args);
        meterian.init();
        return meterian;
    }

    private Meterian(Configuration config, EnvVars environment, PrintStream logger, String args) throws IOException {
        this.config = config;
        this.args = args;
        this.environment = environment;
        this.console = logger;
        this.shell = new Shell();
    }

    private void init() throws IOException {
        HttpClient httpClient = new HttpClientFactory().newHttpClient(config);
        clientJar = new ClientDownloader(httpClient, config.getMeterianBaseUrl(), console).load();
    }

    public Result run(String... extraClientArgs) throws IOException {
        List<String> finalJvmArgs = compose(config.getJvmArgs(), mandatoryJvmArgs());
        List<String> finalClientArgs = compose(args, extraClientArgs);

        log.info("url:  {}", config.getMeterianBaseUrl());
        log.info("jvm:  {}", finalJvmArgs);
        log.info("args: {}", finalClientArgs);

        Result result = new Result();
        Task task = shell.exec(commands(finalJvmArgs, finalClientArgs), options(result));
        task.waitFor();
        result.exitCode = task.exitValue();

        return result;
    }

    private String[] mandatoryJvmArgs() {
        return new String[] {
                "-Dcli.param.folder=" + environment.get("WORKSPACE")
        };
    }

    private List<String> compose(String standardArgs, String[] extraArgs) {
        List<String> args = new ArrayList<String>();
        
        if (standardArgs != null) {
            for (String s: standardArgs.split(" ")) {
                if (!s.trim().isEmpty())
                    args.add(s);
            }
        }
        
        if (extraArgs != null) args.addAll(Arrays.asList(extraArgs));

        return args;
    }

    private String[] commands(List<String> finalJvmArgs, List<String> finalClientArgs) {
        List<String> commands = new ArrayList<>();
        commands.add("java");
        for (String arg: finalJvmArgs)
            commands.add(arg);
        commands.add("-jar");
        commands.add(clientJar.getAbsolutePath());
        for (String arg: finalClientArgs)
            commands.add(arg);

        log.info("Commands: {}", commands);
        return commands.toArray(new String[commands.size()]);
    }

    private Options options(Result result) {
        LineGobbler gobbler = new LineGobbler() {
            int count = 0;

            @Override
            public void process(String type, String line) {
                log.info(line);

                console.print("[meterian] ");
                console.println(line);
                if (++count % 10 == 0)
                    console.flush();

                try {
                    parseMeterianReportURLIfPresent(line);
                } catch (URISyntaxException e) {
                    log.warn("Unexpected", e);
                }
            }

            private void parseMeterianReportURLIfPresent(String line) throws URISyntaxException {
                if (line.indexOf("http") == -1 || line.indexOf("meterian.") == -1)
                    return;

                log.debug("Possible URL found in line {}", line);
                String[] tokens = line.split(" ");
                for (String token : tokens) {
                    if (token.startsWith("http")) {
                        UUID pid = null;
                        String branch = null;
                        URI url = new URI(token);
                        List<NameValuePair> params = URLEncodedUtils.parse(url, Charset.forName("UTF-8"));
                        for (NameValuePair param : params) {
                            if ("branch".equalsIgnoreCase(param.getName())) {
                                branch = param.getValue();
                                log.debug("Meterian project branch?: {}", branch);
                            } else if ("pid".equalsIgnoreCase(param.getName())) {
                                pid = UUID.fromString(param.getValue());
                                log.debug("Meterian project UUID?: {}", pid);
                            }
                        }

                        if (branch != null && pid != null) {
                            result.projectBranch = branch;
                            result.projectUUID = pid;
                            result.reportUrl = url;
                            log.info("Meterian project info: {}", result);
                        }
                    }
                }
            }
        };

        this.environment.put("METERIAN_API_TOKEN", config.getToken());
        log.info("Using config token: {}", config.getToken() != null ? "yes" : "no");

        return new Options()
                .withOutputGobbler(gobbler)
                .withErrorGobbler(gobbler)
                .withEnvironmentVariables(this.environment);
    }

}
