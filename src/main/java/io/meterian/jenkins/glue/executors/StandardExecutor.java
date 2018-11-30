package io.meterian.jenkins.glue.executors;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import io.meterian.jenkins.core.Meterian;

public class StandardExecutor implements MeterianExecutor {

    private StepContext context;

    public StandardExecutor(StepContext context) {
        this.context = context;
    }

    @Override
    public void run(Meterian client) throws Exception {
        Meterian.Result result = client.run("--interactive=false");

        if (result.exitCode != 0) {
            context.setResult(Result.FAILURE);
        }
    }

}
