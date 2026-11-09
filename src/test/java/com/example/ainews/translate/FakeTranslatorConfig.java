package com.example.ainews.translate;

import com.example.ainews.repo.ArticleRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * E2E テスト用に、外部の翻訳 API を呼ばない偽の翻訳エンジンを差し込む。
 * 「【訳】」を先頭に付けて返し、タイトルに "QUOTA" を含む記事では利用上限エラーを返す。
 */
@TestConfiguration
public class FakeTranslatorConfig {

    public static final String PREFIX = "【訳】";

    @Bean
    @Primary
    TranslationService fakeTranslationService(ArticleRepository repo) {
        return new TranslationService(repo, new Translator() {
            @Override
            public String name() {
                return "テスト用";
            }

            @Override
            public List<String> toJapanese(List<String> texts, String sourceLang) throws Exception {
                if (texts.stream().anyMatch(t -> t.contains("QUOTA"))) {
                    throw new TranslationQuotaException("翻訳の利用上限に達しました");
                }
                return texts.stream().map(t -> PREFIX + t).toList();
            }
        });
    }
}
