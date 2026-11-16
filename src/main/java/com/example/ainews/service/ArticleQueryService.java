package com.example.ainews.service;

import com.example.ainews.model.Article;
import com.example.ainews.repo.ArticleRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ArticleQueryService {

    public enum SortOrder {
        NEW, POPULAR;

        public static SortOrder of(String s) {
            return "popular".equalsIgnoreCase(s) ? POPULAR : NEW;
        }
    }

    public enum Period {
        DAY("24時間", Duration.ofDays(1)), THREE_DAYS("3日", Duration.ofDays(3)),
        WEEK("1週間", Duration.ofDays(7)), ALL("すべて", null);

        public final String label;
        public final Duration duration;

        Period(String label, Duration duration) {
            this.label = label;
            this.duration = duration;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Period of(String s) {
            for (Period p : values()) {
                if (p.key().equalsIgnoreCase(s)) {
                    return p;
                }
            }
            return ALL;
        }
    }

    public record Query(SortOrder sort, Period period, String source, String lang, String keyword, int page, int size) {

        /**
         * 同じ話題の記事を 1 件にまとめて表示するか。
         * 出典・言語・キーワードで絞り込んだときは、条件に合う記事をそのまま全部出す。
         */
        public boolean grouped() {
            return isBlank(source) && isBlank(lang) && isBlank(keyword);
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }

    private final ArticleRepository repo;

    public ArticleQueryService(ArticleRepository repo) {
        this.repo = repo;
    }

    public Page<Article> search(Query q) {
        Sort sort = q.sort() == SortOrder.POPULAR
                ? Sort.by(Sort.Order.desc("score"), Sort.Order.desc("publishedAt"))
                : Sort.by(Sort.Order.desc("publishedAt"));
        return repo.findAll(spec(q), PageRequest.of(Math.max(0, q.page()), q.size(), sort));
    }

    /**
     * 代表記事ごとに、同じ話題を扱う他の記事（古い順）を返す。キーは代表記事の ID。
     */
    public Map<Long, List<Article>> related(List<Article> leads) {
        Map<Long, Article> leadByCluster = new HashMap<>();
        for (Article a : leads) {
            if (a.getClusterSize() > 1 && a.getClusterId() != null) {
                leadByCluster.put(a.getClusterId(), a);
            }
        }
        Map<Long, List<Article>> result = new HashMap<>();
        if (leadByCluster.isEmpty()) {
            return result;
        }
        for (Article other : repo.findByClusterIdIn(leadByCluster.keySet())) {
            Article lead = leadByCluster.get(other.getClusterId());
            if (lead != null && !lead.getId().equals(other.getId())) {
                result.computeIfAbsent(lead.getId(), k -> new ArrayList<>()).add(other);
            }
        }
        result.values().forEach(list -> list.sort(Comparator.comparing(Article::getPublishedAt)));
        return result;
    }

    public List<String> sources() {
        return repo.findSourceNames();
    }

    public long count() {
        return repo.count();
    }

    private static Specification<Article> spec(Query q) {
        return (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q.grouped()) {
                ps.add(cb.isTrue(root.get("clusterLead")));
            }
            if (q.period().duration != null) {
                ps.add(cb.greaterThan(root.get("publishedAt"), Instant.now().minus(q.period().duration)));
            }
            if (q.source() != null && !q.source().isBlank()) {
                ps.add(cb.equal(root.get("sourceName"), q.source()));
            }
            if ("ja".equals(q.lang())) {
                ps.add(cb.like(root.get("language"), "ja%"));
            } else if ("foreign".equals(q.lang())) {
                ps.add(cb.notLike(root.get("language"), "ja%"));
            }
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String like = "%" + q.keyword().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("summary")), like, '\\'),
                        cb.like(cb.lower(root.get("titleJa")), like, '\\')));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
