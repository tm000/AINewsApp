package com.example.ainews.crawler;

import com.example.ainews.config.AiNewsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Hacker News（Algolia 検索 API・キー不要）から AI 関連の話題を取得する。
 * ポイントとコメント数を注目度の材料に使う。
 */
@Component
public class HackerNewsCrawler {

    public static final String NAME = "Hacker News";
    private static final Logger log = LoggerFactory.getLogger(HackerNewsCrawler.class);
    private static final String API = "https://hn.algolia.com/api/v1/search";
    static final Pattern AI_TITLE = Pattern.compile(
            "(?i)\\b(AI|A\\.I\\.|AGI|LLMs?|GPT[-\\w]*|ChatGPT|OpenAI|Anthropic|Claude|Gemini|DeepMind|Llama|Mistral|"
                    + "machine learning|deep learning|neural|transformer|diffusion|chatbot|copilot|agentic|"
                    + "generative|artificial intelligence|Hugging ?Face|Nvidia)\\b");

    private final HttpFetcher http;
    private final AiNewsProperties.HackerNews config;
    private final ObjectMapper mapper = new ObjectMapper();

    public HackerNewsCrawler(HttpFetcher http, AiNewsProperties props) {
        this.http = http;
        this.config = props.getHackerNews();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public List<FetchedArticle> crawl() throws Exception {
        long since = Instant.now().minus(Duration.ofDays(3)).getEpochSecond();
        Map<String, FetchedArticle> byId = new LinkedHashMap<>();
        Exception last = null;
        for (String q : config.getQueries()) {
            String url = API + "?tags=story&hitsPerPage=50&query=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                    + "&numericFilters=" + URLEncoder.encode("created_at_i>" + since + ",points>=" + config.getMinPoints(), StandardCharsets.UTF_8);
            try {
                for (FetchedArticle a : parse(http.getString(url))) {
                    byId.merge(a.discussionUrl(), a, (x, y) -> x);
                }
            } catch (Exception e) {
                log.warn("Hacker News query '{}' failed: {}", q, e.toString());
                last = e;
            }
        }
        if (byId.isEmpty() && last != null) {
            throw last;
        }
        return new ArrayList<>(byId.values());
    }

    List<FetchedArticle> parse(String json) throws Exception {
        List<FetchedArticle> result = new ArrayList<>();
        JsonNode root = mapper.readTree(json);
        for (JsonNode hit : root.path("hits")) {
            String title = hit.path("title").asText(null);
            if (title == null || !AI_TITLE.matcher(title).find()) {
                continue;
            }
            String id = hit.path("objectID").asText();
            String discussion = "https://news.ycombinator.com/item?id=" + id;
            String url = hit.path("url").asText(null);
            if (url == null || url.isBlank()) {
                url = discussion;
            }
            String host = TextUtils.hostOf(url);
            String sourceName = discussion.equals(url) || host == null ? NAME : host;
            String text = TextUtils.plainText(hit.path("story_text").asText(null), 400);
            result.add(new FetchedArticle(
                    url, title, text, sourceName, TextUtils.siteOf(url), NAME, null,
                    Instant.ofEpochSecond(hit.path("created_at_i").asLong(Instant.now().getEpochSecond())),
                    TextUtils.detectLanguage(title, "en"), 1.0,
                    hit.path("points").asInt(0), hit.path("num_comments").asInt(0), discussion));
        }
        return result;
    }
}
