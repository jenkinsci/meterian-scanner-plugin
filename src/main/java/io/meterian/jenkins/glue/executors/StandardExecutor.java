package io.meterian.jenkins.glue.executors;

import org.jenkinsci.plugins.workflow.steps.StepContext;

import io.meterian.jenkins.core.Meterian;

public class StandardExecutor implements MeterianExecutor {

    public StandardExecutor(StepContext context) {
    }

    @Override
    public void run(Meterian client) throws Exception {
        client.run();
    }

}
