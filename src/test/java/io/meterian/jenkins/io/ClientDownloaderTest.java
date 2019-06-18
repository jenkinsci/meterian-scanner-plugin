package io.meterian.jenkins.io;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import io.meterian.test_management.TestManagement;
import org.junit.Test;

public class ClientDownloaderTest {

    private static final String BASE_URL = "https://www.meterian.com";

    private TestManagement testManagement = new TestManagement();

    @Test
    public void shouldDownloadTheClientWhenTheHomeDirectoryIsNotFound() throws IOException {
        ClientDownloader.CACHE_FOLDER.delete();

        new ClientDownloader(testManagement.newHttpClient(), BASE_URL, testManagement.nullPrintStream()).load();

        assertTrue(ClientDownloader.ETAG_FILE.exists());
        assertTrue(ClientDownloader.JAR_FILE.exists());
    }

    @Test
    public void shouldDownloadTheClientWhenEtagWasNotFound() throws IOException {
        ClientDownloader.CACHE_FOLDER.mkdirs();
        ClientDownloader.JAR_FILE.createNewFile();
        ClientDownloader.ETAG_FILE.delete();

        new ClientDownloader(testManagement.newHttpClient(), BASE_URL, testManagement.nullPrintStream()).load();

        assertTrue(ClientDownloader.ETAG_FILE.exists());
        assertTrue(ClientDownloader.JAR_FILE.exists());
    }

    @Test
    public void shouldDownloadClientWhenNotFound() throws IOException {
        ClientDownloader.CACHE_FOLDER.mkdirs();
        ClientDownloader.ETAG_FILE.createNewFile();
        ClientDownloader.JAR_FILE.delete();
        
        new ClientDownloader(testManagement.newHttpClient(), BASE_URL, testManagement.nullPrintStream()).load();
        
        assertTrue(ClientDownloader.JAR_FILE.exists());
    }
}
