package io.meterian.jenkins.glue.clientrunners;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import io.meterian.jenkins.core.Meterian;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.Callable;

@SuppressWarnings("rawtypes")
public class ClientRunner {
    private static final Logger log = LoggerFactory.getLogger(ClientRunner.class);

    private Meterian client;
    private PrintStream jenkinsLogger;

    private Callable<Void> setJenkinsBuildToBreak;

    public ClientRunner(Meterian client,
                        AbstractBuild build,
                        PrintStream jenkinsLogger) {
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;

        setJenkinsBuildToBreak = () -> { build.setResult(Result.FAILURE); return null; };
    }

    public ClientRunner(Meterian client,
                        StepContext context,
                        PrintStream jenkinsLogger) {
        this.client = client;
        this.jenkinsLogger = jenkinsLogger;

        setJenkinsBuildToBreak = () -> { context.setResult(Result.FAILURE); return null; };
    }

    public int execute() {
        int executionResult = -1;
        try {
            Meterian.Result buildResult = client.run();
            if (failedAnalysis(buildResult)) {
                breakBuild();

                String clientFailedMsg = String.format("Meterian client analysis failed with exit code %d", buildResult.exitCode);
                log.error(clientFailedMsg);
                jenkinsLogger.println(clientFailedMsg);
            }
            executionResult = buildResult.exitCode;
        } catch (Exception ex) {
            log.warn("Unexpected", ex);
            jenkinsLogger.println("Unexpected exception!");
            ex.printStackTrace(jenkinsLogger);
        }
        return executionResult;
    }

    private boolean failedAnalysis(Meterian.Result buildResult) {
        return buildResult.exitCode != 0;
    }

    public boolean userHasUsedTheAutofixFlag() {
        return client.getFinalClientArgs().contains("--autofix");
    }

    public void breakBuild() throws Exception {
        setJenkinsBuildToBreak.call();

        String clientFailedMsg = String.format("[meterian] Breaking build");
        log.error(clientFailedMsg);
        jenkinsLogger.println(clientFailedMsg);
    }
}
