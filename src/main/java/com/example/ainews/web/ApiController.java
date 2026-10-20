package com.example.ainews.web;

import com.example.ainews.service.ArticleQueryService;
import com.example.ainews.service.ArticleQueryService.Period;
import com.example.ainews.service.ArticleQueryService.Query;
import com.example.ainews.service.ArticleQueryService.SortOrder;
import com.example.ainews.service.CrawlService;
import com.example.ainews.translate.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ArticleQueryService query;
    private final CrawlService crawl;
    private final TranslationService translation;

    public ApiController(ArticleQueryService query, CrawlService crawl, TranslationService translation) {
        this.query = query;
        this.crawl = crawl;
        this.translation = translation;
    }

    @GetMapping("/articles")
    public Map<String, Object> articles(@RequestParam(defaultValue = "new") String sort,
                                        @RequestParam(defaultValue = "all") String period,
                                        @RequestParam(required = false) String source,
                                        @RequestParam(required = false) String lang,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "30") int size) {
        Page<ArticleDto> result = query.search(new Query(SortOrder.of(sort), Period.of(period), source, lang, q, page,
                Math.min(Math.max(size, 1), 100))).map(ArticleDto::of);
        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalItems", result.getTotalElements());
    }

    @PostMapping("/articles/{id}/translate")
    public ResponseEntity<?> translate(@PathVariable long id) {
        if (!translation.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "翻訳は無効になっています"));
        }
        try {
            return translation.translate(id)
                    .<ResponseEntity<?>>map(a -> ResponseEntity.ok(ArticleDto.of(a)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.warn("translate failed: {}: {}", id, e.toString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "翻訳に失敗しました: " + e.getMessage()));
        }
    }

    @PostMapping("/crawl")
    public ResponseEntity<Map<String, Object>> crawlNow() {
        if (crawl.isRunning()) {
            return ResponseEntity.accepted().body(Map.of("started", false, "message", "更新中です"));
        }
        Thread.startVirtualThread(crawl::crawlAll);
        return ResponseEntity.accepted().body(Map.of("started", true));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Instant last = crawl.getLastCrawl();
        List<CrawlService.SourceStatus> sources = crawl.getStatus();
        return Map.of(
                "crawling", crawl.isRunning(),
                "lastCrawl", last == null ? "" : last.toString(),
                "articles", query.count(),
                "translator", translation.translatorName(),
                "sources", sources);
    }
}
