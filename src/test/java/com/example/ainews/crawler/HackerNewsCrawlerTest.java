package com.example.ainews.crawler;

import com.example.ainews.config.AiNewsProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HackerNewsCrawlerTest {

    @Test
    void keepsOnlyAiStoriesAndReadsEngagement() throws Exception {
        AiNewsProperties props = new AiNewsProperties();
        HackerNewsCrawler crawler = new HackerNewsCrawler(new HttpFetcher(props), props);
        String json = new String(getClass().getResourceAsStream("/sample-hn.json").readAllBytes(), StandardCharsets.UTF_8);

        List<FetchedArticle> items = crawler.parse(json);

        assertThat(items).extracting(FetchedArticle::title)
                .containsExactly("OpenAI releases a new reasoning model", "Ask HN: Which LLM do you use for coding?");
        FetchedArticle first = items.get(0);
        assertThat(first.points()).isEqualTo(420);
        assertThat(first.comments()).isEqualTo(180);
        assertThat(first.sourceName()).isEqualTo("example.com");
        assertThat(first.discussionUrl()).isEqualTo("https://news.ycombinator.com/item?id=1001");
        // URL の無い Ask HN は議論ページそのものを記事とする
        assertThat(items.get(1).url()).isEqualTo("https://news.ycombinator.com/item?id=1003");
        assertThat(items.get(1).sourceName()).isEqualTo("Hacker News");
    }
}
