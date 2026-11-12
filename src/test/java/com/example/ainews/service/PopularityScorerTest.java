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
    void groupsSameStoryAndPicksMostPopularAsLead() {
        Article hot = article("A", "OpenAI launches GPT-6 reasoning model", Duration.ofHours(5), 300);
        hot.setId(10L);
        Article sameStory = article("B", "OpenAI launches GPT-6, a new reasoning model", Duration.ofHours(4), 0);
        sameStory.setId(3L);
        Article japanese1 = article("C", "ソフトバンクが生成AIの新会社を設立", Duration.ofHours(2), 0);
        japanese1.setId(5L);
        Article japanese2 = article("D", "ソフトバンク、生成AIの新会社を設立へ", Duration.ofHours(3), 0);
        japanese2.setId(6L);
        Article quiet = article("E", "Small startup raises seed round for robotics", Duration.ofHours(4), 0);
        quiet.setId(7L);

        scorer.scoreAll(List.of(hot, sameStory, japanese1, japanese2, quiet), now);

        assertThat(hot.getClusterId()).isEqualTo(3L);
        assertThat(sameStory.getClusterId()).isEqualTo(3L);
        assertThat(hot.isClusterLead()).isTrue();
        assertThat(sameStory.isClusterLead()).isFalse();
        assertThat(hot.getClusterSize()).isEqualTo(2);
        // 日本語のタイトルも 2 文字ずつに分けて比べるので、同じ話題としてまとまる
        assertThat(japanese1.getClusterId()).isEqualTo(5L).isEqualTo(japanese2.getClusterId());
        assertThat(japanese1.isClusterLead() ^ japanese2.isClusterLead()).isTrue();
        // 似た記事が無ければ自分だけのまとまり
        assertThat(quiet.getClusterId()).isEqualTo(7L);
        assertThat(quiet.isClusterLead()).isTrue();
        assertThat(quiet.getClusterSize()).isEqualTo(1);
    }

    @Test
    void olderArticlesDecay() {
        Article fresh = article("A", "x", Duration.ofHours(1), 50);
        Article old = article("A", "x", Duration.ofHours(48), 50);
        assertThat(scorer.score(fresh, now)).isGreaterThan(scorer.score(old, now));
    }
}
