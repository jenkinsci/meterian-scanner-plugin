package io.meterian.jenkins.glue.clientrunners;

import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.executors.MeterianExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class MultiStageClientRunner implements ClientRunner {
    static final Logger log = LoggerFactory.getLogger(MultiStageClientRunner.class);

    private Meterian client;
    private MeterianExecutor executor;
    private PrintStream jenkinsLogger;

    public MultiStageClientRunner(MeterianExecutor executor, Meterian client,
                           PrintStream jenkinsLogger) {
        this.client = client;
        this.executor = executor;
        this.jenkinsLogger = jenkinsLogger;
    }

    public void execute() {
        try {
            executor.run(client);
        } catch (Exception ex) {
            log.warn("Unexpected", ex);
            jenkinsLogger.println("Unexpected exception!");
            ex.printStackTrace(jenkinsLogger);
        }
    }
}
