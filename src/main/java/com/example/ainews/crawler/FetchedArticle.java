package com.example.ainews.crawler;

import java.time.Instant;

/** クローラが取得した生の記事データ。保存前の中間形式 */
public record FetchedArticle(
        String url,
        String title,
        String summary,
        String sourceName,
        String sourceSite,
        String fetchedVia,
        String imageUrl,
        Instant publishedAt,
        String language,
        double sourceWeight,
        int points,
        int comments,
        String discussionUrl
) {
}
