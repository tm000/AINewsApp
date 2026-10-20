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
        boolean hasDeepL = cfg.getDeeplApiKey() != null && !cfg.getDeeplApiKey().isBlank();
        if (provider.isEmpty()) {
            provider = hasDeepL ? "deepl" : "mymemory";
        }
        return switch (provider) {
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
        List<String> texts = new ArrayList<>();
        texts.add(a.getTitle());
        if (a.getSummary() != null) {
            texts.add(a.getSummary());
        }
        List<String> ja = translator.toJapanese(texts, a.getLanguage());
        a.setTitleJa(truncate(ja.get(0), 1000));
        if (ja.size() > 1) {
            a.setSummaryJa(truncate(ja.get(1), 3900));
        }
        return Optional.of(repo.save(a));
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
