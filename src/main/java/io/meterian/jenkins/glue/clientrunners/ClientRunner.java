package io.meterian.jenkins.glue.clientrunners;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import io.meterian.jenkins.core.Meterian;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class ClientRunner {
    private static final Logger log = LoggerFactory.getLogger(ClientRunner.class);

    private AbstractBuild build;
    private StepContext context;
    private Meterian client;
    private PrintStream jenkinsLogger;

    public ClientRunner(AbstractBuild build,
                        Meterian client,
                        PrintStream jenkinsLogger) {
        this.build = build;
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;
    }

    public ClientRunner(Meterian client,
                        StepContext context,
                        PrintStream jenkinsLogger) {
        this.client = client;
        this.context = context;
        this.jenkinsLogger = jenkinsLogger;
    }

    public void execute() {
        try {
            Meterian.Result buildResult = client.run();
            if (buildResult.exitCode != 0) {
                if (build != null) {
                    build.setResult(Result.FAILURE);
                } else if (context != null)  {
                    context.setResult(Result.FAILURE);
                }
            }
        } catch (Exception ex) {
            log.warn("Unexpected", ex);
            jenkinsLogger.println("Unexpected exception!");
            ex.printStackTrace(jenkinsLogger);
        }
    }

    public boolean userHasUsedTheAutofixFlag() {
        return client.getFinalClientArgs().contains("--autofix");
    }
}
