package io.meterian.jenkins.glue.clientrunners;

import hudson.model.Result;
import io.meterian.jenkins.core.Meterian;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class MultiStageClientRunner implements ClientRunner {
    static final Logger log = LoggerFactory.getLogger(MultiStageClientRunner.class);

    private Meterian client;
    private PrintStream jenkinsLogger;
    private StepContext context;

    public MultiStageClientRunner(Meterian client,
                                  StepContext context, PrintStream jenkinsLogger) {
        this.client = client;
        this.context = context;
        this.jenkinsLogger = jenkinsLogger;
    }

    @Override
    public void execute() {
        try {
            Meterian.Result buildResult = client.run();
            if (buildResult.exitCode != 0) {
                context.setResult(Result.FAILURE);
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
