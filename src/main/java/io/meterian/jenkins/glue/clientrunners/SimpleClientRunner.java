package io.meterian.jenkins.glue.clientrunners;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import io.meterian.jenkins.core.Meterian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class SimpleClientRunner implements ClientRunner {
    static final Logger log = LoggerFactory.getLogger(SimpleClientRunner.class);

    private AbstractBuild build;
    private Meterian client;
    private PrintStream jenkinsLogger;

    public SimpleClientRunner(AbstractBuild build,
                              Meterian client,
                              PrintStream jenkinsLogger) {
        this.build = build;
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;
    }

    @Override
    public void execute() {
        try {
            Meterian.Result buildResult = client.run();
            if (buildResult.exitCode != 0) {
                build.setResult(Result.FAILURE);
            }
        } catch (Exception ex) {
            log.warn("Unexpected", ex);
            jenkinsLogger.println("Unexpected exception!");
            ex.printStackTrace(jenkinsLogger);
        }
    }

    @Override
    public boolean userHasUsedTheAutofixFlag() {
        return client.getFinalClientArgs().contains("--autofix");
    }
}
