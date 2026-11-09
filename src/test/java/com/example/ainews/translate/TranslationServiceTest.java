package com.example.ainews.translate;

import com.example.ainews.model.Article;
import com.example.ainews.repo.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranslationServiceTest {

    private final ArticleRepository repo = mock(ArticleRepository.class);

    private static Article foreign(String title) {
        Article a = new Article();
        a.setTitle(title);
        a.setSummary("summary of " + title);
        a.setLanguage("en");
        a.setPublishedAt(Instant.now());
        return a;
    }

    /** まとめて翻訳できる偽のエンジン。呼ばれた回数を数え、"QUOTA" を含むと上限エラーにする */
    private static class BulkTranslator implements Translator {
        final AtomicInteger calls = new AtomicInteger();

        @Override public String name() { return "bulk"; }
        @Override public boolean supportsBulk() { return true; }
        @Override public List<String> toJapanese(List<String> texts, String lang) { throw new UnsupportedOperationException(); }

        @Override
        public List<Digest> digest(List<DigestRequest> items) throws Exception {
            calls.incrementAndGet();
            if (items.stream().anyMatch(r -> r.title().contains("QUOTA"))) {
                throw new TranslationQuotaException("上限");
            }
            return items.stream().map(r -> new Digest("訳:" + r.title(), "・要約")).toList();
        }
    }

    @Test
    void translatesPendingArticlesInBatchesAndStopsAtQuota() {
        List<Article> pending = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pending.add(foreign("story " + i));
        }
        pending.add(foreign("QUOTA story"));
        pending.add(foreign("never reached"));
        when(repo.findUntranslatedForeign(any(Pageable.class))).thenReturn(pending);
        BulkTranslator translator = new BulkTranslator();

        int done = new TranslationService(repo, translator).translatePending(60, 2);

        // 2 件ずつ: [0,1] [2,3] [4,QUOTA] で上限 → 打ち切り
        assertThat(done).isEqualTo(4);
        assertThat(translator.calls.get()).isEqualTo(3);
        assertThat(pending.get(0).getTitleJa()).isEqualTo("訳:story 0");
        assertThat(pending.get(0).getSummaryJa()).isEqualTo("・要約");
        assertThat(pending.get(4).getTitleJa()).isNull();
        assertThat(pending.get(6).getTitleJa()).isNull();
    }

    @Test
    void skipsBulkTranslationForFreeEngines() {
        Translator free = new Translator() {
            @Override public String name() { return "free"; }
            @Override public List<String> toJapanese(List<String> texts, String lang) { return texts; }
        };
        TranslationService service = new TranslationService(repo, free);

        assertThat(service.isBulkEnabled()).isFalse();
        assertThat(service.translatePending(60, 10)).isZero();
        verify(repo, never()).findUntranslatedForeign(any(Pageable.class));
    }
}
