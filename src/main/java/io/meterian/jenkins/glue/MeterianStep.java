package io.meterian.jenkins.glue;

import static io.meterian.jenkins.glue.Toilet.getConfiguration;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.executors.GerritExecutor;
import io.meterian.jenkins.glue.executors.MeterianExecutor;
import io.meterian.jenkins.glue.executors.StandardExecutor;
import io.meterian.scm.gerrit.Gerrit;

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
            PrintStream logger = getContext().get(TaskListener.class).getLogger();
            EnvVars environment = getContext().get(EnvVars.class);

            Meterian client = Meterian.build(
                    getConfiguration(),
                    environment,
                    logger,
                    args);

            MeterianExecutor executor;
            if (Gerrit.isSupported(environment)) {
                executor = new GerritExecutor(getContext());
            } else {
                executor = new StandardExecutor(getContext());
            }

            try {
                executor.run(client);
            } catch (Exception ex) {
                log.warn("Unexpected", ex);
                logger.println("Unxpected exception!");
                ex.printStackTrace(logger);
            }
            
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

}