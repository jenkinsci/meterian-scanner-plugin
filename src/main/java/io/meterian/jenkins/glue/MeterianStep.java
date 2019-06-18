package io.meterian.jenkins.glue;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import io.meterian.jenkins.autofixfeature.AutoFixFeature;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.clientrunners.ClientRunner;
import io.meterian.jenkins.glue.executors.GerritExecutor;
import io.meterian.jenkins.glue.executors.MeterianExecutor;
import io.meterian.jenkins.glue.executors.StandardExecutor;
import io.meterian.scm.gerrit.Gerrit;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;

import static io.meterian.jenkins.glue.Toilet.getConfiguration;

public class MeterianStep extends Step {

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

            if (! client.requiredEnvironmentVariableHasBeenSet()) {
                return null;
            }

            client.prepare("--interactive=false");

            MeterianExecutor executor;
            ClientRunner clientRunner =
                    new ClientRunner(client, getContext(), jenkinsLogger);

            if (Gerrit.isSupported(environment)) {
                executor = new GerritExecutor(getContext());
            } else {
                AutoFixFeature autoFixFeature = new AutoFixFeature(
                        configuration,
                        environment,
                        clientRunner,
                        jenkinsLogger
                );
                executor = new StandardExecutor(clientRunner, autoFixFeature);
            }
            
            executor.run(client);
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

}
