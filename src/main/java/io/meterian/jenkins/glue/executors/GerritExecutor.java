package io.meterian.jenkins.glue.executors;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.common.FileInfo;
import com.meterian.common.io.SimpleFileCompare;
import com.meterian.common.io.SimpleFileCompare.Diff;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.meterian.jenkins.core.Meterian;
import io.meterian.jenkins.core.Meterian.Result;
import io.meterian.scm.gerrit.Gerrit;
import io.meterian.scm.gerrit.GerritRoboComment;

public class GerritExecutor implements MeterianExecutor {

    private static final Logger log = LoggerFactory.getLogger(GerritExecutor.class);

    private final EnvVars environment;
    private final PrintStream logger;
    private final Run<?,?> run;

    public GerritExecutor(StepContext context) throws IOException, InterruptedException  {
        environment = context.get(EnvVars.class);
        logger = context.get(TaskListener.class).getLogger();
        run = context.get(Run.class);
    }

    @Override
    public void run(Meterian client) throws Exception {

        Gerrit gerrit = Gerrit.build(environment,run, logger);

        List<String> manifests = isGerritRunRequired(gerrit, logger);
        if (manifests.isEmpty()) {
            logger.println("[meterian] No change on a manifest files was detected, no analysys needed :)");
            return;
        }
        
        logger.println("[meterian] A critical change on a manifest file was detected - running Meterian analysis...");
        client.prepare("--interactive=false", "--autofix:readonly");
        Result result = client.run();

        logger.format("[meterian] Checking %d manifest file(s) %n", manifests.size());
        generateRobotComments(gerrit, manifests, result);
    }

    private void generateRobotComments(Gerrit gerrit, List<String> manifests, Result result) throws IOException {
        List<GerritRoboComment> comments = new ArrayList<>();

        File root = new File(environment.get("WORKSPACE"));
        for (String manifest : manifests) {
            File fixFile = new File(root, manifest+".fix");
            if (!fixFile.exists()) {
                logger.format("[meterian] No fixes found for file %s %n", manifest);
                continue;
            }
            
            SimpleFileCompare fc = new SimpleFileCompare(new File(root, manifest), fixFile);
            List<Diff> diffs = fc.compare();
            log.info("Manifest {}, diffs {}", manifest, diffs);

            GerritRoboComment comment = new GerritRoboComment(manifest, diffs, result.reportUrl);
            comments.add(comment);
        }
        
        gerrit.apply(comments);
    }

    private List<String> isGerritRunRequired(Gerrit gerrit, PrintStream logger) throws IOException {

        List<String> manifests = new ArrayList<String>();

        Map<String, FileInfo> files = gerrit.getChangedFiles();
        if (files != null && files.size() > 0) {
            logger.format("[meterian] A change was detected on %d files %n", files.size());
            for (String path : files.keySet()) {
                logger.format("[meterian] - %s %n", path);
                if (path.contains("pom.xml"))
                    manifests.add(path);
            }
        }
        
        return manifests;
    }
}
