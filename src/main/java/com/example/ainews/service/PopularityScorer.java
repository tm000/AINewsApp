package com.example.ainews.service;

import com.example.ainews.crawler.TextUtils;
import com.example.ainews.model.Article;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        int n = articles.size();
        List<Set<String>> tokens = articles.stream().map(a -> TextUtils.titleTokens(a.getTitle())).toList();
        int[] coverage = new int[n];
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            if (tokens.get(i).size() < 3) {
                continue;
            }
            Article a = articles.get(i);
            for (int j = i + 1; j < n; j++) {
                Article b = articles.get(j);
                if (tokens.get(j).size() < 3 || b.getSourceName().equals(a.getSourceName())) {
                    continue;
                }
                if (Duration.between(a.getPublishedAt(), b.getPublishedAt()).abs().compareTo(COVERAGE_WINDOW) > 0) {
                    continue;
                }
                if (TextUtils.jaccard(tokens.get(i), tokens.get(j)) >= SIMILARITY_THRESHOLD) {
                    coverage[i]++;
                    coverage[j]++;
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            Article a = articles.get(i);
            a.setCoverage(coverage[i]);
            a.setScore(score(a, now));
        }
        assignClusters(articles, parent);
    }

    /** 同じ話題の記事をまとめ、注目度が一番高い記事をまとまりの代表にする */
    private static void assignClusters(List<Article> articles, int[] parent) {
        Map<Integer, List<Article>> groups = new HashMap<>();
        for (int i = 0; i < articles.size(); i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(articles.get(i));
        }
        Comparator<Article> best = Comparator.comparingDouble(Article::getScore).reversed()
                .thenComparing(Article::getPublishedAt);
        for (List<Article> group : groups.values()) {
            Long clusterId = group.stream().map(Article::getId).filter(Objects::nonNull).min(Long::compare).orElse(null);
            Article lead = group.stream().min(best).orElseThrow();
            for (Article a : group) {
                a.setClusterId(clusterId);
                a.setClusterLead(a == lead);
                a.setClusterSize(group.size());
            }
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    public double score(Article a, Instant now) {
        double base = a.getPoints() + a.getComments() * 2.0 + a.getCoverage() * 15.0 + a.getSourceWeight() * 10.0;
        double hours = Math.max(0, Duration.between(a.getPublishedAt(), now).toMinutes() / 60.0);
        return base / Math.pow(hours + 2, GRAVITY);
    }
}
