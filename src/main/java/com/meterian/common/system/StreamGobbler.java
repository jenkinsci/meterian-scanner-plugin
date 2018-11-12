package com.meterian.common.system;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamGobbler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Shell.class);

    private final InputStream input;
    private final String type;
    private final LineGobbler gobbler;
    private final CountDownLatch latch;
    // private final Map<Key, String> mdc;

    StreamGobbler(InputStream input, String type, LineGobbler gobbler, CountDownLatch latch) {
        this.input = input;
        this.type = type;
        this.gobbler = gobbler;
        this.latch = latch;
        // this.mdc = mdcCaptureSnapshot();
    }

    @Override
    public void run() {
        // mdcInstallSnapshot(this.mdc);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            try {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    gobbler.process(type, line);
                }
            } finally {
                reader.close();
            }
        } catch (IOException ex) {
             log.warn("Unexpected exception gobbling " + type + " stream", ex);
        } finally {
            latch.countDown();
            // mdcReset();
        }
    }
}