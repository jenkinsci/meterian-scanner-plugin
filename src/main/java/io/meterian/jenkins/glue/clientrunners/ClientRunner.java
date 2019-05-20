package io.meterian.jenkins.glue.clientrunners;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import io.meterian.jenkins.core.Meterian;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.Callable;

public class ClientRunner {
    private static final Logger log = LoggerFactory.getLogger(ClientRunner.class);
    private Callable setJenkinsResultStatus;

    private Meterian client;
    private PrintStream jenkinsLogger;

    public ClientRunner(Meterian client,
                        AbstractBuild build,
                        PrintStream jenkinsLogger) {
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;

        setJenkinsResultStatus = () -> { build.setResult(Result.FAILURE); return null; };
    }

    public ClientRunner(Meterian client,
                        StepContext context,
                        PrintStream jenkinsLogger) {
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;

        setJenkinsResultStatus = () -> { context.setResult(Result.FAILURE); return null; };
    }

    public void execute() {
        try {
            Meterian.Result buildResult = client.run();
            if (buildResult.exitCode != 0) {
                setJenkinsResultStatus.call();
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
