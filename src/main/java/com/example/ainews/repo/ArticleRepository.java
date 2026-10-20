package com.example.ainews.repo;

import com.example.ainews.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    Optional<Article> findByUrl(String url);

    List<Article> findByPublishedAtAfter(Instant since);

    List<Article> findTop100ByImageUrlIsNullAndImageLookupDoneFalseOrderByPublishedAtDesc();

    @Query("select distinct a.sourceName from Article a order by a.sourceName")
    List<String> findSourceNames();

    @Modifying
    @Query("delete from Article a where a.publishedAt < :before")
    int deleteOlderThan(Instant before);
}
