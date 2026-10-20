package com.example.ainews.service;

import com.example.ainews.config.AiNewsProperties;
import com.example.ainews.crawler.FetchedArticle;
import com.example.ainews.crawler.HackerNewsCrawler;
import com.example.ainews.crawler.OgImageFetcher;
import com.example.ainews.crawler.RssCrawler;
import com.example.ainews.model.Article;
import com.example.ainews.repo.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** 定期的に全フィードを巡回する */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    public record SourceStatus(String name, Instant lastRun, int fetched, String error) {}

    private final AiNewsProperties props;
    private final RssCrawler rss;
    private final HackerNewsCrawler hn;
    private final OgImageFetcher ogImage;
    private final ArticleIngestService ingest;
    private final ArticleRepository repo;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, SourceStatus> status = new ConcurrentHashMap<>();
    private volatile Instant lastCrawl;

    public CrawlService(AiNewsProperties props, RssCrawler rss, HackerNewsCrawler hn, OgImageFetcher ogImage,
                        ArticleIngestService ingest, ArticleRepository repo) {
        this.props = props;
        this.rss = rss;
        this.hn = hn;
        this.ogImage = ogImage;
        this.ingest = ingest;
        this.repo = repo;
    }

    @Scheduled(initialDelayString = "${ainews.crawl.initial-delay:PT5S}", fixedDelayString = "${ainews.crawl.interval:PT30M}")
    public void scheduledCrawl() {
        crawlAll();
    }

    public boolean isRunning() {
        return running.get();
    }

    public Instant getLastCrawl() {
        return lastCrawl;
    }

    public List<SourceStatus> getStatus() {
        return status.values().stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    /** @return 実行したら true、すでに実行中なら false */
    public boolean crawlAll() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            Duration retention = Duration.ofDays(props.getCrawl().getRetentionDays());
            Instant cutoff = Instant.now().minus(retention);
            List<String> names = new ArrayList<>();
            List<Callable<List<FetchedArticle>>> jobs = new ArrayList<>();
            for (AiNewsProperties.Feed feed : props.getFeeds()) {
                names.add(feed.getName());
                jobs.add(() -> rss.crawl(feed));
            }
            if (hn.isEnabled()) {
                names.add(HackerNewsCrawler.NAME);
                jobs.add(hn::crawl);
            }

            int added = 0;
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<List<FetchedArticle>>> futures = pool.invokeAll(jobs);
                // RSS を先に保存し、その後 HN のポイントを同じ URL の記事に足す
                for (int i = 0; i < futures.size(); i++) {
                    String name = names.get(i);
                    try {
                        List<FetchedArticle> list = futures.get(i).get().stream()
                                .filter(a -> a.publishedAt().isAfter(cutoff))
                                .toList();
                        added += ingest.ingest(list);
                        status.put(name, new SourceStatus(name, Instant.now(), list.size(), null));
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        log.warn("crawl failed: {}: {}", name, cause.toString());
                        status.put(name, new SourceStatus(name, Instant.now(), 0, cause.getMessage()));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }

            fillMissingImages();
            ingest.purgeOlderThan(retention);
            ingest.rescore(retention);
            lastCrawl = Instant.now();
            log.info("crawl done: {} new articles", added);
            return true;
        } finally {
            running.set(false);
        }
    }

    /** RSS に画像が無かった記事について、記事ページの og:image を取りに行く */
    private void fillMissingImages() {
        List<Article> targets = repo.findTop100ByImageUrlIsNullAndImageLookupDoneFalseOrderByPublishedAtDesc()
                .stream().limit(props.getCrawl().getOgImageFetchLimit()).toList();
        if (targets.isEmpty()) {
            return;
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Article a : targets) {
                pool.submit(() -> {
                    a.setImageUrl(ogImage.fetch(a.getUrl()));
                    a.setImageLookupDone(true);
                });
            }
        }
        repo.saveAll(targets);
    }
}
