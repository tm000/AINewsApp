package com.example.ainews.web;

import com.example.ainews.crawler.FetchedArticle;
import com.example.ainews.model.Article;
import com.example.ainews.repo.ArticleRepository;
import com.example.ainews.service.ArticleIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        "ainews.crawl.initial-delay=PT24H",
        "ainews.translate.provider=none"
})
@AutoConfigureMockMvc
class AppIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ArticleIngestService ingest;
    @Autowired ArticleRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        Instant now = Instant.now();
        ingest.ingest(List.of(
                new FetchedArticle("https://example.com/new?utm_source=rss", "Brand new AI story", "summary", "Example",
                        "https://example.com", "Example", "https://example.com/a.jpg", now.minus(Duration.ofMinutes(10)), "en", 1.0, 0, 0, null),
                new FetchedArticle("https://example.com/hot", "Hot AI story", null, "Example",
                        "https://example.com", "Example", null, now.minus(Duration.ofHours(6)), "en", 1.0, 0, 0, null),
                new FetchedArticle("https://example.jp/ja", "国内のAIニュース", null, "日本の媒体",
                        "https://example.jp", "日本の媒体", null, now.minus(Duration.ofHours(1)), "ja", 1.0, 0, 0, null)));
        // HN 側で同じ URL の記事が見つかったら、ポイントだけが足される
        ingest.ingest(List.of(new FetchedArticle("https://www.example.com/hot/", "Hot AI story (HN)", null, "example.com",
                "https://example.com", "Hacker News", null, now.minus(Duration.ofHours(6)), "en", 1.0, 800, 300,
                "https://news.ycombinator.com/item?id=1")));
        ingest.rescore(Duration.ofDays(30));
    }

    @Test
    void mergesHackerNewsIntoExistingArticle() {
        assertThat(repo.count()).isEqualTo(3);
        Article hot = repo.findByUrl("https://example.com/hot").orElseThrow();
        assertThat(hot.getSourceName()).isEqualTo("Example");
        assertThat(hot.getPoints()).isEqualTo(800);
    }

    @Test
    void apiSortsByNewestAndPopularity() throws Exception {
        mvc.perform(get("/api/articles").param("sort", "new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Brand new AI story"));
        mvc.perform(get("/api/articles").param("sort", "popular"))
                .andExpect(jsonPath("$.items[0].title").value("Hot AI story"));
        mvc.perform(get("/api/articles").param("lang", "ja"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void pageRendersSourceAndThumbnail() throws Exception {
        mvc.perform(get("/").param("sort", "popular"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hot AI story")))
                .andExpect(content().string(containsString("https://example.com/a.jpg")))
                .andExpect(content().string(containsString("日本の媒体")));
        mvc.perform(get("/sources")).andExpect(status().isOk());
    }
}
