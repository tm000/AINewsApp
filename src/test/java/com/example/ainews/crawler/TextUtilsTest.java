package com.example.ainews.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextUtilsTest {

    @Test
    void normalizeUrlRemovesTrackingAndWww() {
        assertThat(TextUtils.normalizeUrl("https://www.Example.com/a/b/?utm_source=x&id=3&fbclid=y#top"))
                .isEqualTo("https://example.com/a/b?id=3");
        assertThat(TextUtils.normalizeUrl("https://example.com/"))
                .isEqualTo("https://example.com/");
    }

    @Test
    void detectsJapanese() {
        assertThat(TextUtils.detectLanguage("生成AIのニュース", "en")).isEqualTo("ja");
        assertThat(TextUtils.detectLanguage("New AI model", "en")).isEqualTo("en");
    }

    @Test
    void ogImageExtraction() {
        String html = "<html><head><meta property=\"og:image\" content=\"/img/a.jpg\"></head></html>";
        assertThat(OgImageFetcher.extract(html, "https://site.example/post")).isEqualTo("https://site.example/img/a.jpg");
    }
}
