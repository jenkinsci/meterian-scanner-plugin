package io.meterian.jenkins.glue.executors;

import io.meterian.jenkins.autofixfeature.AutoFixFeature;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.clientrunners.ClientRunner;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class StandardExecutor implements MeterianExecutor {

    private StepContext context;
    private ClientRunner clientRunner;
    private AutoFixFeature autoFixFeature;

    public StandardExecutor(StepContext context,
                            ClientRunner clientRunner,
                            AutoFixFeature autoFixFeature) {
        this.context = context;
        this.clientRunner = clientRunner;
        this.autoFixFeature = autoFixFeature;
    }

    @Override
    public void run(Meterian client) throws Exception {
        if (clientRunner.userHasUsedTheAutofixFlag()) {
            autoFixFeature.execute();
        } else {
            clientRunner.execute();
        }
    }
}
