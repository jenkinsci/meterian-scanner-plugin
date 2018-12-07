package io.meterian.jenkins.glue;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import io.meterian.jenkins.PullRequestCreator;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.executors.GerritExecutor;
import io.meterian.jenkins.glue.executors.MeterianExecutor;
import io.meterian.jenkins.glue.executors.StandardExecutor;
import io.meterian.scm.gerrit.Gerrit;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;

import static io.meterian.jenkins.glue.Toilet.getConfiguration;

public class MeterianStep extends Step {

    private static final Logger log = LoggerFactory.getLogger(MeterianStep.class);

    private final String args;

    @DataBoundConstructor
    public MeterianStep(String args) {
        this.args = args;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(args, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "meterian";
        }

        @Override
        public String getDisplayName() {
            return "Meterian analysis";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }

    public static class Execution extends SynchronousStepExecution<Void> {

        private final String args;

        Execution(String message, StepContext context) throws IOException, InterruptedException {
            super(context);
            this.args = message;
        }

        @Override
        protected Void run() throws Exception {
            PrintStream jenkinsLogger = getContext().get(TaskListener.class).getLogger();
            EnvVars environment = getContext().get(EnvVars.class);

            MeterianPlugin.Configuration configuration = getConfiguration();
            Meterian client = Meterian.build(
                    configuration,
                    environment,
                    jenkinsLogger,
                    args);

            MeterianExecutor executor;
            if (Gerrit.isSupported(environment)) {
                executor = new GerritExecutor(getContext());
            } else {
                executor = new StandardExecutor(getContext());
            }

            client.prepare("--interactive=false");
            if (executor instanceof StandardExecutor) {
                if (userHasUsedTheAutofixFlag(client)) {
                    new PullRequestCreator(
                            configuration,
                            environment.get("WORKSPACE"),
                            client,
                            executor,
                            jenkinsLogger
                    ).execute();
                } else {
                    runMeterianClient(executor, client, jenkinsLogger);
                }
            } else {
                runMeterianClient(executor, client, jenkinsLogger);
            }
            return null;
        }

        private void runMeterianClient(
                MeterianExecutor executor,
                Meterian client,
                PrintStream jenkinsLogger) {
            try {
                executor.run(client);
            } catch (Exception ex) {
                log.warn("Unexpected", ex);
                jenkinsLogger.println("Unexpected exception!");
                ex.printStackTrace(jenkinsLogger);
            }
        }

        private boolean userHasUsedTheAutofixFlag(Meterian client) {
            return client.getFinalClientArgs().contains("--autofix");
        }

        private static final long serialVersionUID = 1L;

    }

}