package io.meterian.jenkins.io;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientDownloader {

    private static final Logger log = LoggerFactory.getLogger(ClientDownloader.class);
    
    public static final String JAR_FILENAME = "meterian-cli.jar";
    public static final File CACHE_FOLDER = new File(System.getProperty("user.home"), ".meterian");
    public static final File JAR_FILE = new File(CACHE_FOLDER, JAR_FILENAME);
    public static final File ETAG_FILE = new File(CACHE_FOLDER, JAR_FILENAME + ".etag");

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final HttpClient client;
    private final String url;
    private final PrintStream console;

    public ClientDownloader(HttpClient client, String baseurl, PrintStream console) {
        this.client = client;
        this.url = HttpClientFactory.makeUrl(baseurl, "latest-client-canary");
        this.console = console;
    }

    public File load() throws IOException {
        CACHE_FOLDER.mkdirs();

        URI uri = newURI(url);

        boolean found = false;
        log.debug("etagFile: {} (exist={})", ETAG_FILE, ETAG_FILE.exists());
        log.debug("cachedFile: {} (exist={})", JAR_FILE, JAR_FILE.exists());
        if (JAR_FILE.exists() && ETAG_FILE.exists()) {
            log.debug("etagFile and cachedFile found");
            String cachedEtag = readContents(ETAG_FILE);
            String currentEtag = getEtag(uri);
            found = currentEtag.equals(cachedEtag);
        }

        if (!found) {
            console.println("[meterian] Downloading the latest meterian client...");
            log.debug("Etag not matching, downloading client from url {}", uri);
            updateFiles(uri, JAR_FILE, ETAG_FILE);
        } else {
            log.debug("etag matches, using cached client");
        }

        console.println("[meterian] The Meterian client is ready to work");
        return JAR_FILE;
    }

    private URI newURI(String urlstring) throws IOException {
        try {
            return new URI(urlstring);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private void updateFiles(URI uri, File cachedFile, File etagFile) throws IOException{
        HttpResponse response = client.execute(new HttpGet(uri));
        try {
            if (status(response) == 200) {
                Files.write(etagFile.toPath(), getEtagValue(response).getBytes(UTF_8));
                try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(cachedFile))) {
                    response.getEntity().writeTo(out);
                }
                log.debug("Successfully updated files");
            } else {
                log.warn("Unable to update files, response {}", response);
                throw new IOException("Unable to get access to the Meterian client at "+uri);
            }
        } finally {
            EntityUtils.consume(response.getEntity());
        }
    }

    private String readContents(File cachedEtagFile) {
        try {
            return Files.readAllLines(cachedEtagFile.toPath(), UTF_8).get(0);
        } catch (Exception any) {
            return "";
        }
    }

    private String getEtag(URI uri) throws IOException {
        final HttpResponse response = client.execute(new HttpHead(uri));
        try {
            return status(response) == 200 ? getEtagValue(response) : "";
        } finally {
            EntityUtils.consume(response.getEntity());
        }
    }

    private String getEtagValue(final HttpResponse response) {
        return response.getFirstHeader("ETag").getValue();
    }

    private int status(final HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }
}
