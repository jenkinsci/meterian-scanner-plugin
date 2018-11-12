package io.meterian.jenkins.core;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
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

    private static final Logger log = LoggerFactory.getLogger(Meterian.class);

    private final Configuration config;
    private final EnvVars environment;
    private final PrintStream console;
    private final String args;
    private final Shell shell;

    private File clientJar;
    
    private UUID projectUUID;
    private String projectBranch;
    
    public static Meterian build(Configuration config, EnvVars environment, PrintStream logger, String args) throws IOException {
        Meterian meterian = new Meterian(config, environment, logger, args);
        meterian.init();
        return meterian;
    }
    
    private  Meterian(Configuration config, EnvVars environment, PrintStream logger, String args) throws IOException {
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
    
    public int run(String... extraClientArgs) throws IOException {
        String finalJvmArgs = compose(config.getJvmArgs(), mandatoryJvmArgs());
        String finalClientArgs = compose(args, extraClientArgs);
        
        log.info("url:  {}", config.getUrl());
        log.info("jvm:  {}", finalJvmArgs);
        log.info("args: {}", finalClientArgs);

        Task task = shell.exec(commands(finalJvmArgs, finalClientArgs), options());
        task.waitFor();
        return task.exitValue();
    }

    private String[] mandatoryJvmArgs() {
        return new String[] {
            "-Dcli.param.folder="+environment.get("WORKSPACE")
        };
    }

    private String compose(String standardArgs, String[] extraArgs) {
        StringBuilder sb = new StringBuilder();
        if (standardArgs != null && standardArgs.length() > 0) {
            sb.append(standardArgs);
        }

        for (String arg : extraArgs ) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(arg);
        }

        return sb.toString();
    }

    private String[] commands(String finalJvmArgs, String finalClientArgs) {
        List<String> commands = new ArrayList<>();
        commands.add("java");
        commands.add(finalJvmArgs);
        commands.add("-jar");
        commands.add(clientJar.getAbsolutePath());
        commands.add(finalClientArgs);
        
        log.info("Commands: {}",commands);
        return commands.toArray(new String[commands.size()]);
    }

    private Options options() {
        LineGobbler gobbler = new LineGobbler() {
            int count = 0;
            
            @Override
            public void process(String type, String line) {
                console.print("[meterian] ");
                console.println(line);
                if (++count%10 == 0)
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
                        List<NameValuePair> params = URLEncodedUtils.parse(new URI(token), Charset.forName("UTF-8"));
                        for (NameValuePair param : params) {
                            if ("branch".equalsIgnoreCase(param.getName())) {
                                projectBranch = param.getValue();
                                log.info("Meterian project branch: {}", projectBranch);
                            }
                            else if ("pid".equalsIgnoreCase(param.getName())) {
                                projectUUID = UUID.fromString(param.getValue());
                                log.info("Meterian project UUID: {}", projectUUID);
                            }
                        }
                    }
                }
            }
        };
        
        return new Options()
                .withOutputGobbler(gobbler)
                .withErrorGobbler(gobbler)
                .withEnvironmentVariables(this.environment);
    }

}
