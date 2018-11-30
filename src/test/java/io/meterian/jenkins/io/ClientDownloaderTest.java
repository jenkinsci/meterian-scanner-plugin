package io.meterian.jenkins.io;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.client.HttpClient;
import org.junit.Before;
import org.junit.Test;

import io.meterian.jenkins.io.ClientDownloader;
import io.meterian.jenkins.io.HttpClientFactory;

public class ClientDownloaderTest {

    private static final String BASE_URL = "https://www.meterian.com";

    @Test
    public void shouldDownloadTheClientWhenTheHomeDirectoryIsNotFound() throws IOException {
        ClientDownloader.CACHE_FOLDER.delete();

        new ClientDownloader(newHttpClient(), BASE_URL, nullPrintStream()).load();

        assertTrue(ClientDownloader.ETAG_FILE.exists());
        assertTrue(ClientDownloader.JAR_FILE.exists());
    }

    @Test
    public void shouldDownloadTheClientWhenEtagWasNotFound() throws IOException {
        ClientDownloader.CACHE_FOLDER.mkdirs();
        ClientDownloader.JAR_FILE.createNewFile();
        ClientDownloader.ETAG_FILE.delete();

        new ClientDownloader(newHttpClient(), BASE_URL, nullPrintStream()).load();

        assertTrue(ClientDownloader.ETAG_FILE.exists());
        assertTrue(ClientDownloader.JAR_FILE.exists());
    }

    @Test
    public void shouldDownloadClientWhenNotFound() throws IOException {
        ClientDownloader.CACHE_FOLDER.mkdirs();
        ClientDownloader.ETAG_FILE.createNewFile();
        ClientDownloader.JAR_FILE.delete();
        
        new ClientDownloader(newHttpClient(), BASE_URL, nullPrintStream()).load();
        
        assertTrue(ClientDownloader.JAR_FILE.exists());
    }
 
    private PrintStream nullPrintStream() {
        return new PrintStream(new NullOutputStream());
    }

    private static HttpClient newHttpClient() {
        return new HttpClientFactory().newHttpClient(new HttpClientFactory.Config() {
            @Override
            public int getHttpConnectTimeout() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getHttpSocketTimeout() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getHttpMaxTotalConnections() {
                return 100;
            }

            @Override
            public int getHttpMaxDefaultConnectionsPerRoute() {
                return 100;
            }

            @Override
            public String getHttpUserAgent() {
                // TODO Auto-generated method stub
                return null;
            }});
    }

}
