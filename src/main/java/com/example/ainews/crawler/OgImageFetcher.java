package com.example.ainews.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 記事ページの og:image / twitter:image からサムネイル URL を取る */
@Component
public class OgImageFetcher {

    private final HttpFetcher http;

    public OgImageFetcher(HttpFetcher http) {
        this.http = http;
    }

    public String fetch(String pageUrl) {
        String host = TextUtils.hostOf(pageUrl);
        if (host == null || host.endsWith("news.google.com") || host.endsWith("news.ycombinator.com")) {
            return null;
        }
        try {
            byte[] body = http.getBytes(pageUrl);
            // head だけ見れば十分なので先頭 300KB に限定
            int len = Math.min(body.length, 300_000);
            return extract(new String(body, 0, len, StandardCharsets.UTF_8), pageUrl);
        } catch (Exception e) {
            return null;
        }
    }

    static String extract(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        for (String selector : new String[]{
                "meta[property=og:image:secure_url]", "meta[property=og:image]",
                "meta[name=twitter:image]", "meta[name=twitter:image:src]"}) {
            Element el = doc.selectFirst(selector);
            if (el != null) {
                String url = el.absUrl("content");
                if (url.isEmpty()) {
                    url = el.attr("content");
                }
                if (url.startsWith("http")) {
                    return url;
                }
            }
        }
        return null;
    }
}
