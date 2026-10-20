package com.example.ainews.crawler;

import com.example.ainews.config.AiNewsProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 外部サイトへの HTTP GET をまとめたもの */
@Component
public class HttpFetcher {

    private final HttpClient client;
    private final String userAgent;

    public HttpFetcher(AiNewsProperties props) {
        this.userAgent = props.getCrawl().getUserAgent();
        this.client = HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public byte[] getBytes(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", userAgent)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, application/json;q=0.9, */*;q=0.8")
                .GET()
                .build();
        HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() >= 400) {
            throw new IOException("HTTP " + res.statusCode() + " " + url);
        }
        return res.body();
    }

    public String getString(String url) throws IOException, InterruptedException {
        return new String(getBytes(url), java.nio.charset.StandardCharsets.UTF_8);
    }
}
