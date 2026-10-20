package com.example.ainews.crawler;

import com.example.ainews.config.AiNewsProperties;
import com.example.ainews.config.AiNewsProperties.Feed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RssCrawlerTest {

    private final RssCrawler crawler = new RssCrawler(new HttpFetcher(new AiNewsProperties()));

    private byte[] resource(String name) throws Exception {
        return getClass().getResourceAsStream("/" + name).readAllBytes();
    }

    @Test
    void parsesItemsWithImagesAndSummary() throws Exception {
        Feed feed = new Feed("Sample", "https://www.example.com/feed", "en", 1.2, false);
        List<FetchedArticle> items = crawler.parse(feed, resource("sample-rss.xml"));

        assertThat(items).hasSize(3);
        FetchedArticle first = items.get(0);
        assertThat(first.title()).isEqualTo("OpenAI releases a new reasoning model");
        assertThat(first.summary()).isEqualTo("The company said the model beats previous versions.");
        assertThat(first.imageUrl()).isEqualTo("https://cdn.example.com/img/openai.jpg");
        assertThat(first.sourceName()).isEqualTo("Sample");
        assertThat(first.sourceSite()).isEqualTo("https://www.example.com");
        assertThat(first.language()).isEqualTo("en");

        // 1x1 のトラッキング画像は飛ばして本文の画像を使う
        assertThat(items.get(1).imageUrl()).isEqualTo("https://cdn.example.com/img/eu.png");
        // enclosure の画像、日本語タイトルは ja と判定
        assertThat(items.get(2).imageUrl()).isEqualTo("https://cdn.example.com/img/ja.jpg");
        assertThat(items.get(2).language()).isEqualTo("ja");
    }

    @Test
    void aggregatorUsesItemSourceAsOrigin() throws Exception {
        Feed feed = new Feed("Google ニュース", "https://news.google.com/rss", "ja", 0.8, true);
        List<FetchedArticle> items = crawler.parse(feed, resource("sample-googlenews.xml"));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).sourceName()).isEqualTo("日経新聞");
        assertThat(items.get(0).sourceSite()).isEqualTo("https://www.nikkei.com");
        assertThat(items.get(0).title()).isEqualTo("国内企業が生成AIを全社導入");
    }
}
