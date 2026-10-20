package com.example.ainews.service;

import com.example.ainews.crawler.TextUtils;
import com.example.ainews.model.Article;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 注目度スコアの計算。
 * <pre>
 *   基礎点 = HNポイント + HNコメント数×2 + 他媒体での掲載数×15 + 媒体の重み×10
 *   スコア = 基礎点 / (経過時間[h] + 2)^1.5
 * </pre>
 * Hacker News のランキング式と同じく、反響が大きいほど上がり、時間が経つほど下がる。
 */
@Component
public class PopularityScorer {

    static final double GRAVITY = 1.5;
    static final double SIMILARITY_THRESHOLD = 0.5;
    static final Duration COVERAGE_WINDOW = Duration.ofHours(48);

    public void scoreAll(List<Article> articles, Instant now) {
        List<Set<String>> tokens = articles.stream().map(a -> TextUtils.titleTokens(a.getTitle())).toList();
        for (int i = 0; i < articles.size(); i++) {
            Article a = articles.get(i);
            int coverage = 0;
            if (tokens.get(i).size() >= 3) {
                for (int j = 0; j < articles.size(); j++) {
                    Article b = articles.get(j);
                    if (i == j || b.getSourceName().equals(a.getSourceName())) {
                        continue;
                    }
                    if (Duration.between(a.getPublishedAt(), b.getPublishedAt()).abs().compareTo(COVERAGE_WINDOW) > 0) {
                        continue;
                    }
                    if (TextUtils.jaccard(tokens.get(i), tokens.get(j)) >= SIMILARITY_THRESHOLD) {
                        coverage++;
                    }
                }
            }
            a.setCoverage(coverage);
            a.setScore(score(a, now));
        }
    }

    public double score(Article a, Instant now) {
        double base = a.getPoints() + a.getComments() * 2.0 + a.getCoverage() * 15.0 + a.getSourceWeight() * 10.0;
        double hours = Math.max(0, Duration.between(a.getPublishedAt(), now).toMinutes() / 60.0);
        return base / Math.pow(hours + 2, GRAVITY);
    }
}
