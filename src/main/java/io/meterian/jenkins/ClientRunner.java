package io.meterian.jenkins;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.executors.MeterianExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;

public class ClientRunner {
    static final Logger log = LoggerFactory.getLogger(ClientRunner.class);

    private AbstractBuild build;
    private Meterian client;
    private MeterianExecutor executor;
    private PrintStream jenkinsLogger;

    public ClientRunner(AbstractBuild build,
                        Meterian client,
                        PrintStream jenkinsLogger) {
        this.build = build;
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;
    }

    public ClientRunner(MeterianExecutor executor, Meterian client,
                        PrintStream jenkinsLogger) {
        this.client = client;
        this.executor = executor;
        this.jenkinsLogger = jenkinsLogger;
    }

    public void execute() throws IOException {
        if (executor == null) {
            Meterian.Result buildResult = client.run();
            if (buildResult.exitCode != 0) {
                build.setResult(Result.FAILURE);
            }
        } else {
            try {
                executor.run(client);
            } catch (Exception ex) {
                log.warn("Unexpected", ex);
                jenkinsLogger.println("Unexpected exception!");
                ex.printStackTrace(jenkinsLogger);
            }
        }
    }
}
