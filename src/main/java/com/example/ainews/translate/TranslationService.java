package com.example.ainews.translate;

import com.example.ainews.config.AiNewsProperties;
import com.example.ainews.crawler.HttpFetcher;
import com.example.ainews.model.Article;
import com.example.ainews.repo.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 記事の日本語訳を作り、DB にキャッシュする（同じ記事は 2 回翻訳しない） */
@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private final ArticleRepository repo;
    private final Translator translator;

    @Autowired
    public TranslationService(ArticleRepository repo, AiNewsProperties props, HttpFetcher http) {
        this.repo = repo;
        this.translator = createTranslator(props.getTranslate(), http);
        log.info("translator: {}", translator == null ? "none" : translator.name());
    }

    TranslationService(ArticleRepository repo, Translator translator) {
        this.repo = repo;
        this.translator = translator;
    }

    static Translator createTranslator(AiNewsProperties.Translate cfg, HttpFetcher http) {
        String provider = cfg.getProvider() == null ? "" : cfg.getProvider().trim().toLowerCase(Locale.ROOT);
        boolean hasClaude = cfg.getAnthropicApiKey() != null && !cfg.getAnthropicApiKey().isBlank();
        boolean hasDeepL = cfg.getDeeplApiKey() != null && !cfg.getDeeplApiKey().isBlank();
        if (provider.isEmpty()) {
            provider = hasClaude ? "claude" : hasDeepL ? "deepl" : "mymemory";
        }
        return switch (provider) {
            case "claude" -> hasClaude ? new ClaudeTranslator(cfg.getAnthropicApiKey().trim(), cfg.getClaudeModel()) : null;
            case "deepl" -> hasDeepL ? new DeepLTranslator(cfg.getDeeplApiKey().trim()) : null;
            case "mymemory" -> new MyMemoryTranslator(http, cfg.getMymemoryEmail());
            default -> null;
        };
    }

    public boolean isEnabled() {
        return translator != null;
    }

    public String translatorName() {
        return translator == null ? "なし" : translator.name();
    }

    @Transactional
    public Optional<Article> translate(long articleId) throws Exception {
        Optional<Article> found = repo.findById(articleId);
        if (found.isEmpty()) {
            return found;
        }
        Article a = found.get();
        if (!a.isForeign() || translator == null || (a.getTitleJa() != null && (a.getSummary() == null || a.getSummaryJa() != null))) {
            return found;
        }
        Translator.Digest d = translator.digest(List.of(request(a))).get(0);
        if (d == null) {
            throw new java.io.IOException("翻訳結果が返りませんでした");
        }
        apply(a, d);
        return Optional.of(repo.save(a));
    }

    /** 巡回のたびに新着記事をまとめて翻訳できるエンジンか */
    public boolean isBulkEnabled() {
        return translator != null && translator.supportsBulk();
    }

    /**
     * まだ翻訳していない海外記事を新しい順に最大 limit 件、batchSize 件ずつまとめて翻訳する。
     * 利用上限などで失敗したら、そこで打ち切る（残りは次の巡回で続きから）。
     *
     * @return 翻訳した件数
     */
    public int translatePending(int limit, int batchSize) {
        if (!isBulkEnabled() || limit <= 0) {
            return 0;
        }
        List<Article> targets = repo.findUntranslatedForeign(PageRequest.of(0, limit));
        int done = 0;
        for (int i = 0; i < targets.size(); i += Math.max(1, batchSize)) {
            List<Article> batch = targets.subList(i, Math.min(targets.size(), i + Math.max(1, batchSize)));
            try {
                List<Translator.Digest> res = translator.digest(batch.stream().map(TranslationService::request).toList());
                List<Article> changed = new ArrayList<>();
                for (int j = 0; j < batch.size() && j < res.size(); j++) {
                    if (res.get(j) != null) {
                        apply(batch.get(j), res.get(j));
                        changed.add(batch.get(j));
                    }
                }
                repo.saveAll(changed);
                done += changed.size();
            } catch (Exception e) {
                log.warn("bulk translation stopped: {}", e.toString());
                break;
            }
        }
        if (done > 0) {
            log.info("translated {} articles", done);
        }
        return done;
    }

    private static Translator.DigestRequest request(Article a) {
        return new Translator.DigestRequest(a.getTitle(), a.getSummary(), a.getLanguage());
    }

    private static void apply(Article a, Translator.Digest d) {
        a.setTitleJa(truncate(d.titleJa(), 1000));
        if (d.summaryJa() != null) {
            a.setSummaryJa(truncate(d.summaryJa(), 3900));
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
