package com.example.ainews.web;

import com.example.ainews.model.Article;

import java.time.Instant;

public record ArticleDto(
        long id,
        String url,
        String title,
        String titleJa,
        String summary,
        String summaryJa,
        String sourceName,
        String sourceSite,
        String imageUrl,
        Instant publishedAt,
        String language,
        int points,
        int comments,
        String discussionUrl,
        int coverage,
        double score
) {
    public static ArticleDto of(Article a) {
        return new ArticleDto(a.getId(), a.getUrl(), a.getTitle(), a.getTitleJa(), a.getSummary(), a.getSummaryJa(),
                a.getSourceName(), a.getSourceSite(), a.getImageUrl(), a.getPublishedAt(), a.getLanguage(),
                a.getPoints(), a.getComments(), a.getDiscussionUrl(), a.getCoverage(), a.getScore());
    }
}
