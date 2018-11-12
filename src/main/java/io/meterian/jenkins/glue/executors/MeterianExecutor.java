package io.meterian.jenkins.glue.executors;

import io.meterian.jenkins.core.Meterian;

public interface MeterianExecutor {

    public void run(Meterian client) throws Exception 
    ;

}