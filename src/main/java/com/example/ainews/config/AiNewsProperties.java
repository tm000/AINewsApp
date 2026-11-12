package com.example.ainews.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ainews")
public class AiNewsProperties {

    private Crawl crawl = new Crawl();
    private HackerNews hackerNews = new HackerNews();
    private Translate translate = new Translate();
    private List<Feed> feeds = new ArrayList<>();

    public Crawl getCrawl() { return crawl; }
    public void setCrawl(Crawl crawl) { this.crawl = crawl; }
    public HackerNews getHackerNews() { return hackerNews; }
    public void setHackerNews(HackerNews hackerNews) { this.hackerNews = hackerNews; }
    public Translate getTranslate() { return translate; }
    public void setTranslate(Translate translate) { this.translate = translate; }
    public List<Feed> getFeeds() { return feeds; }
    public void setFeeds(List<Feed> feeds) { this.feeds = feeds; }

    public static class Crawl {
        private Duration initialDelay = Duration.ofSeconds(5);
        private Duration interval = Duration.ofMinutes(30);
        private int retentionDays = 30;
        private int ogImageFetchLimit = 40;
        private String userAgent = "Mozilla/5.0 (compatible; AiNewsBot/0.1)";
        /** 「今すぐ更新」を押せる間隔（前回の巡回開始からこの時間は受け付けない） */
        private Duration manualCooldown = Duration.ofMinutes(5);

        public Duration getInitialDelay() { return initialDelay; }
        public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        public int getOgImageFetchLimit() { return ogImageFetchLimit; }
        public void setOgImageFetchLimit(int ogImageFetchLimit) { this.ogImageFetchLimit = ogImageFetchLimit; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        public Duration getManualCooldown() { return manualCooldown; }
        public void setManualCooldown(Duration manualCooldown) { this.manualCooldown = manualCooldown; }
    }

    public static class HackerNews {
        private boolean enabled = true;
        private int minPoints = 10;
        private List<String> queries = new ArrayList<>(List.of("AI", "LLM"));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMinPoints() { return minPoints; }
        public void setMinPoints(int minPoints) { this.minPoints = minPoints; }
        public List<String> getQueries() { return queries; }
        public void setQueries(List<String> queries) { this.queries = queries; }
    }

    public static class Translate {
        private String provider = "";
        private String deeplApiKey = "";
        private String mymemoryEmail = "";
        private String anthropicApiKey = "";
        private String googleApiKey = "";
        private String claudeModel = "claude-haiku-5-5";
        /** 巡回のたびに自動で翻訳する海外記事の最大件数（Claude / DeepL のときだけ） */
        private int autoLimit = 60;
        /** 1 回の API 呼び出しでまとめて翻訳する記事数 */
        private int batchSize = 10;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getDeeplApiKey() { return deeplApiKey; }
        public void setDeeplApiKey(String deeplApiKey) { this.deeplApiKey = deeplApiKey; }
        public String getMymemoryEmail() { return mymemoryEmail; }
        public void setMymemoryEmail(String mymemoryEmail) { this.mymemoryEmail = mymemoryEmail; }
        public String getGoogleApiKey() { return googleApiKey; }
        public void setGoogleApiKey(String googleApiKey) { this.googleApiKey = googleApiKey; }
        public String getAnthropicApiKey() { return anthropicApiKey; }
        public void setAnthropicApiKey(String anthropicApiKey) { this.anthropicApiKey = anthropicApiKey; }
        public String getClaudeModel() { return claudeModel; }
        public void setClaudeModel(String claudeModel) { this.claudeModel = claudeModel; }
        public int getAutoLimit() { return autoLimit; }
        public void setAutoLimit(int autoLimit) { this.autoLimit = autoLimit; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class Feed {
        private String name;
        private String url;
        private String language = "en";
        private double weight = 1.0;
        /** Google ニュースのような集約フィード。記事ごとの出典を item の source 要素から取る */
        private boolean aggregator = false;
        /** 指定すると、タイトルか要約にどれかの語を含む記事だけを取り込む（AI 専門でない媒体用） */
        private List<String> keywords = new ArrayList<>();

        public Feed() {}

        public Feed(String name, String url, String language, double weight, boolean aggregator) {
            this.name = name;
            this.url = url;
            this.language = language;
            this.weight = weight;
            this.aggregator = aggregator;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public boolean isAggregator() { return aggregator; }
        public void setAggregator(boolean aggregator) { this.aggregator = aggregator; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    }
}
