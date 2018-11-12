package io.meterian.jenkins.io;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class HttpClientFactory {

    public static interface Config {

        int getHttpConnectTimeout();

        int getHttpSocketTimeout();

        int getHttpMaxTotalConnections();

        int getHttpMaxDefaultConnectionsPerRoute();

        String getHttpUserAgent();

    }

    public HttpClient newHttpClient(Config config) {
        final HttpClientConnectionManager connectionManager = newConnectionManager(config);
        final RequestConfig requestConfig = newRequestConfig(config);

        final CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .setUserAgent(config.getHttpUserAgent())
                .build();

        return httpClient;
    }

    private RequestConfig newRequestConfig(Config config) {
        Builder requestConfigBuilder = RequestConfig.custom()
                .setConnectTimeout(config.getHttpConnectTimeout())
                .setSocketTimeout(config.getHttpSocketTimeout());

//        if (config.getHttpProxyHost() != null && config.getHttpProxyPort() != null) {
//            HttpHost proxy = new HttpHost(config.getHttpProxyHost(),config.getHttpProxyPort());
//            print(NEWLINE, "Using http proxy: ", proxy, NEWLINE);
//            requestConfigBuilder.setProxy(proxy);
//        }

        final RequestConfig requestConfig = requestConfigBuilder.build();
        return requestConfig;
    }

    private PoolingHttpClientConnectionManager newConnectionManager(Config config) {
        final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build();

        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
        cm.setMaxTotal(config.getHttpMaxTotalConnections());
        cm.setDefaultMaxPerRoute(config.getHttpMaxDefaultConnectionsPerRoute());
        return cm;
    }

    public static String makeUrl(String baseurl, String path) {
        return baseurl.endsWith("/") ? baseurl+path : baseurl+"/"+path;
    }

}
