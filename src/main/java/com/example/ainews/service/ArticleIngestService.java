package com.example.ainews.service;

import com.example.ainews.crawler.FetchedArticle;
import com.example.ainews.crawler.HackerNewsCrawler;
import com.example.ainews.crawler.TextUtils;
import com.example.ainews.model.Article;
import com.example.ainews.repo.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 取得した記事を保存する。同じ URL の記事は 1 件にまとめる */
@Service
public class ArticleIngestService {

    private final ArticleRepository repo;
    private final PopularityScorer scorer;

    public ArticleIngestService(ArticleRepository repo, PopularityScorer scorer) {
        this.repo = repo;
        this.scorer = scorer;
    }

    /** @return 新規に追加した件数 */
    @Transactional
    public int ingest(List<FetchedArticle> fetched) {
        int added = 0;
        Instant now = Instant.now();
        for (FetchedArticle f : fetched) {
            String url = TextUtils.normalizeUrl(f.url());
            if (url == null || f.title() == null || f.title().isBlank()) {
                continue;
            }
            Article a = repo.findByUrl(url).orElse(null);
            boolean fromHn = HackerNewsCrawler.NAME.equals(f.fetchedVia());
            if (a == null) {
                a = new Article();
                a.setUrl(url);
                a.setTitle(f.title());
                a.setSummary(f.summary());
                a.setSourceName(f.sourceName());
                a.setSourceSite(f.sourceSite());
                a.setFetchedVia(f.fetchedVia());
                a.setImageUrl(f.imageUrl());
                a.setPublishedAt(f.publishedAt());
                a.setFetchedAt(now);
                a.setLanguage(f.language());
                a.setSourceWeight(f.sourceWeight());
                added++;
            } else if (!fromHn) {
                // HN で先に見つけた記事を RSS 側の情報（媒体名・画像・要約）で上書きする
                if (HackerNewsCrawler.NAME.equals(a.getFetchedVia())) {
                    a.setTitle(f.title());
                    a.setSourceName(f.sourceName());
                    a.setSourceSite(f.sourceSite());
                    a.setFetchedVia(f.fetchedVia());
                    a.setSourceWeight(f.sourceWeight());
                    a.setLanguage(f.language());
                }
                if (a.getSummary() == null) {
                    a.setSummary(f.summary());
                }
                if (a.getImageUrl() == null && f.imageUrl() != null) {
                    a.setImageUrl(f.imageUrl());
                }
            }
            if (fromHn) {
                a.setPoints(Math.max(a.getPoints(), f.points()));
                a.setComments(Math.max(a.getComments(), f.comments()));
                a.setDiscussionUrl(f.discussionUrl());
            }
            repo.save(a);
        }
        return added;
    }

    @Transactional
    public void rescore(Duration window) {
        Instant now = Instant.now();
        List<Article> recent = repo.findByPublishedAtAfter(now.minus(window));
        scorer.scoreAll(recent, now);
        repo.saveAll(recent);
    }

    @Transactional
    public int purgeOlderThan(Duration age) {
        return repo.deleteOlderThan(Instant.now().minus(age));
    }
}
