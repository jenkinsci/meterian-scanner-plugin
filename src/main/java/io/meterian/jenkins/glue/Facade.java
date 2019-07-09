package io.meterian.jenkins.glue;

import hudson.security.Permission;
import io.meterian.jenkins.glue.MeterianPlugin.Configuration;
import jenkins.model.Jenkins;

public class Facade {

    public static Configuration getConfiguration() {
        try {
            return Jenkins.get().getDescriptorByType(MeterianPlugin.Configuration.class);
        } catch (Exception any) {
            throw new IllegalStateException("Jenkins instance is not ready", any);
        }
    }

    public static void checkPermission(Permission permission) {
        Jenkins.get().checkPermission(permission); 
    }
}
