package com.example.ainews.service;

import com.example.ainews.model.Article;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PopularityScorerTest {

    private final PopularityScorer scorer = new PopularityScorer();
    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");

    private Article article(String source, String title, Duration age, int points) {
        Article a = new Article();
        a.setSourceName(source);
        a.setTitle(title);
        a.setPublishedAt(now.minus(age));
        a.setPoints(points);
        return a;
    }

    @Test
    void engagementAndCoverageRaiseScore() {
        Article hot = article("A", "OpenAI launches GPT-6 reasoning model", Duration.ofHours(5), 300);
        Article sameStory = article("B", "OpenAI launches GPT-6, a new reasoning model", Duration.ofHours(4), 0);
        Article quiet = article("C", "Small startup raises seed round for robotics", Duration.ofHours(4), 0);

        scorer.scoreAll(List.of(hot, sameStory, quiet), now);

        assertThat(sameStory.getCoverage()).isEqualTo(1);
        assertThat(quiet.getCoverage()).isZero();
        assertThat(hot.getScore()).isGreaterThan(sameStory.getScore());
        assertThat(sameStory.getScore()).isGreaterThan(quiet.getScore());
    }

    @Test
    void olderArticlesDecay() {
        Article fresh = article("A", "x", Duration.ofHours(1), 50);
        Article old = article("A", "x", Duration.ofHours(48), 50);
        assertThat(scorer.score(fresh, now)).isGreaterThan(scorer.score(old, now));
    }
}
