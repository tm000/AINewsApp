package com.example.ainews.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "article", indexes = {
        @Index(name = "idx_article_url", columnList = "url", unique = true),
        @Index(name = "idx_article_published", columnList = "publishedAt"),
        @Index(name = "idx_article_score", columnList = "score")
})
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(length = 4000)
    private String summary;

    /** 出典（媒体名） */
    @Column(nullable = false)
    private String sourceName;

    /** 出典サイトのトップページ */
    @Column(length = 1024)
    private String sourceSite;

    /** どのフィード・API から取得したか */
    private String fetchedVia;

    @Column(length = 2048)
    private String imageUrl;

    /** og:image の取得を試したか（無駄な再取得を避ける） */
    private boolean imageLookupDone;

    @Column(nullable = false)
    private Instant publishedAt;

    private Instant fetchedAt;

    /** 記事の言語（ja / en など） */
    private String language;

    private double sourceWeight = 1.0;

    /** Hacker News のポイント・コメント数 */
    private int points;
    private int comments;

    @Column(length = 1024)
    private String discussionUrl;

    /** 同じ話題を扱う他媒体の記事数 */
    private int coverage;

    /** 注目度スコア（時間減衰込み） */
    private double score;

    @Column(length = 1024)
    private String titleJa;

    @Column(length = 4000)
    private String summaryJa;

    public boolean isForeign() {
        return language != null && !language.startsWith("ja");
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceSite() { return sourceSite; }
    public void setSourceSite(String sourceSite) { this.sourceSite = sourceSite; }
    public String getFetchedVia() { return fetchedVia; }
    public void setFetchedVia(String fetchedVia) { this.fetchedVia = fetchedVia; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public boolean isImageLookupDone() { return imageLookupDone; }
    public void setImageLookupDone(boolean imageLookupDone) { this.imageLookupDone = imageLookupDone; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public double getSourceWeight() { return sourceWeight; }
    public void setSourceWeight(double sourceWeight) { this.sourceWeight = sourceWeight; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public int getComments() { return comments; }
    public void setComments(int comments) { this.comments = comments; }
    public String getDiscussionUrl() { return discussionUrl; }
    public void setDiscussionUrl(String discussionUrl) { this.discussionUrl = discussionUrl; }
    public int getCoverage() { return coverage; }
    public void setCoverage(int coverage) { this.coverage = coverage; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getTitleJa() { return titleJa; }
    public void setTitleJa(String titleJa) { this.titleJa = titleJa; }
    public String getSummaryJa() { return summaryJa; }
    public void setSummaryJa(String summaryJa) { this.summaryJa = summaryJa; }
}
