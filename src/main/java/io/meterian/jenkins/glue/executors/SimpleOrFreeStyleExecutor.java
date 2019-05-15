package io.meterian.jenkins.glue.executors;

import io.meterian.jenkins.autofixfeature.AutoFixFeature;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.glue.clientrunners.ClientRunner;

public class SimpleOrFreeStyleExecutor implements MeterianExecutor {

    private AutoFixFeature autoFixFeature;
    private ClientRunner clientRunner;

    public SimpleOrFreeStyleExecutor(AutoFixFeature autoFixFeature,
                              ClientRunner clientRunner) {
        this.autoFixFeature = autoFixFeature;
        this.clientRunner = clientRunner;
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
