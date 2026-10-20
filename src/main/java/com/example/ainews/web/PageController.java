package com.example.ainews.web;

import com.example.ainews.model.Article;
import com.example.ainews.service.ArticleQueryService;
import com.example.ainews.service.ArticleQueryService.Period;
import com.example.ainews.service.ArticleQueryService.Query;
import com.example.ainews.service.ArticleQueryService.SortOrder;
import com.example.ainews.service.CrawlService;
import com.example.ainews.translate.TranslationService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    static final int PAGE_SIZE = 30;

    private final ArticleQueryService query;
    private final CrawlService crawl;
    private final TranslationService translation;

    public PageController(ArticleQueryService query, CrawlService crawl, TranslationService translation) {
        this.query = query;
        this.crawl = crawl;
        this.translation = translation;
    }

    @GetMapping("/")
    public String index(@RequestParam(defaultValue = "new") String sort,
                        @RequestParam(required = false) String period,
                        @RequestParam(required = false) String source,
                        @RequestParam(required = false) String lang,
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        SortOrder sortOrder = SortOrder.of(sort);
        // 注目度順は既定で直近 3 日に絞る（古い記事が上に残らないように）
        Period p = period != null ? Period.of(period) : (sortOrder == SortOrder.POPULAR ? Period.THREE_DAYS : Period.ALL);
        Page<Article> articles = query.search(new Query(sortOrder, p, source, lang, q, page, PAGE_SIZE));

        model.addAttribute("articles", articles);
        model.addAttribute("sort", sortOrder.name().toLowerCase());
        model.addAttribute("period", p.key());
        model.addAttribute("periods", Period.values());
        model.addAttribute("source", source == null ? "" : source);
        model.addAttribute("lang", lang == null ? "" : lang);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sources", query.sources());
        model.addAttribute("total", query.count());
        model.addAttribute("lastCrawl", crawl.getLastCrawl());
        model.addAttribute("crawling", crawl.isRunning());
        model.addAttribute("translationEnabled", translation.isEnabled());
        return "index";
    }

    @GetMapping("/sources")
    public String sources(Model model) {
        model.addAttribute("status", crawl.getStatus());
        model.addAttribute("lastCrawl", crawl.getLastCrawl());
        model.addAttribute("translator", translation.translatorName());
        return "sources";
    }
}
