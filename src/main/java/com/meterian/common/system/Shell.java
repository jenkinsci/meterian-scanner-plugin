package com.meterian.common.system;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.meterian.jenkins.core.Meterian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("singleton")
public class Shell {

    private static final Logger log = LoggerFactory.getLogger(Shell.class);

    public static class Options {
       private LineGobbler outputGobbler = NO_GOBBLER;
       private LineGobbler errorGobbler = NO_GOBBLER;
       private File workingFolder = null;
       private List<String> envps = new ArrayList<>();

       public Options withOutputGobbler(LineGobbler gobbler) {
           this.outputGobbler = gobbler;
           return this;
       }
        
        public Options withErrorGobbler(LineGobbler gobbler) {
        	this.errorGobbler = gobbler;
        	return this;
        }
        
        public Options onDirectory(File folder) {
        	this.workingFolder = folder;
        	return this;
        }

        public Options withEnvironmentVariable(String name, String value) {
            envps.add(name+"="+value);
            return this;
        }
        
        public Options withEnvironmentVariables(Map<String, String> env) {
            for (String name : env.keySet()) {
                String value = env.get(name);
                envps.add(name+"="+value);
            }

            return this;
        }

        public Options withEnvironmentVariableIfNotNull(String name, String value) {
            if (value != null)
                return withEnvironmentVariable(name, value);
            else
                return this;
        }
        private String[] envp() {
        	return envps.toArray(new String[envps.size()]);
        }
        
        public File getWorkingDirectory() {
            return workingFolder;
        }

        public LineGobbler getOutputGobbler() {
            return outputGobbler;
        }

        public LineGobbler getErrorGobbler() {
            return errorGobbler;
        }

        @Override
        public String toString() {
            return "[output=" + toString(outputGobbler) + ", error=" + toString(errorGobbler) + ", folder=" + workingFolder + ", envp=" + envps + "]";
        }

        private String toString(LineGobbler gobbler) {
            return (gobbler == NO_GOBBLER) ? "DEFAULT" : "CUSTOM";
        }

        public String getEnvironmentVariable(String key) {
            for (String envp : envps) {
                String[] tokens = envp.split("=");
                if (key.equals(tokens[0]))
                    return tokens[1];
            }
            return null;
        }

    }

    public static class Task {
        public static void main(String[] args) throws IOException {
            String[] commands = new String[]{
                    "java",
                    "-Dcli.param.folder=/path/to/workspace/TestMeterianPlugin-Freestyle-autofix",
                    "-jar",
                    "/path/to/.meterian/meterian-cli.jar", "--interactive=false"};

            Meterian.Result result = new Meterian.Result();
            Shell shell = new Shell();

            Task task = shell.exec(commands, new Options());
            task.waitFor();
            result.exitCode = task.exitValue();

            System.out.println(result.exitCode);
        }

        public static final long DEFAULT_TIMEOUT_IN_SECONDS = 60L*5L;

        private final Process process;
        private final CountDownLatch ioLatch;

        public Task(Process process) {
            this.process = process;
            this.ioLatch = new CountDownLatch(2);
        }

        public int waitFor() throws IOException {
            return this.waitFor(DEFAULT_TIMEOUT_IN_SECONDS);
        }

        public int waitFor(long timeoutInSeconds) throws IOException {
            try {
                ioLatch.await(timeoutInSeconds, TimeUnit.SECONDS);
                return process.waitFor(timeoutInSeconds, TimeUnit.SECONDS) ? process.exitValue() : -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Operation interrupted!", e);
            }
        }

        public int exitValue() {
            return process.exitValue();
        }

        public void destroy() {
            process.destroy();
        }
    };

    public static final LineGobbler NO_GOBBLER = new LineGobbler() {
        public void process(String type, String text) {
            if (log.isTraceEnabled())
                log.trace("{}> {}", type, text);
        }
    };

    public static final LineGobbler DEBUG_GOBBLER = new LineGobbler() {
        public void process(String type, String text) {
            log.debug("{}> {}", type, text);
        }
    };

    private final ExecutorService threadPool;

    public Shell() {
        this(Executors.newCachedThreadPool());
    }

    public Shell(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public Task exec(String... commands) throws IOException {
        return this.exec(commands, new Options());
    }

    public Task exec(String commands[], Options options) throws IOException {
        if (log.isDebugEnabled())
            log.debug("Running shell commmand {} with options {}",Arrays.asList(commands), options);

        Process process;
        if (options.workingFolder == null)
            process = Runtime.getRuntime().exec(commands, options.envp());
        else
            process = Runtime.getRuntime().exec(commands, options.envp(), options.workingFolder);

        Task task =  new Task(process);
        threadPool.execute(new StreamGobbler(process.getInputStream(), "STDOUT", options.outputGobbler, task.ioLatch));
        threadPool.execute(new StreamGobbler(process.getErrorStream(), "STDERR", options.errorGobbler, task.ioLatch));
        return task;
    }
}
