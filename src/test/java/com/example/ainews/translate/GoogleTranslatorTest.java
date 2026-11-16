package com.example.ainews.translate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTranslatorTest {

    private final GoogleTranslator translator = new GoogleTranslator("test-key");

    @Test
    void parsesTranslations() throws Exception {
        String json = """
                {"data":{"translations":[{"translatedText":"OpenAI が新モデルを公開","detectedSourceLanguage":"en"},
                {"translatedText":"高速です"}]}}""";
        assertThat(translator.parse(200, json)).containsExactly("OpenAI が新モデルを公開", "高速です");
        assertThat(translator.supportsBulk()).isTrue();
        // 無料枠に収めるため、自動翻訳はタイトルだけ
        assertThat(translator.bulkIncludesSummary()).isFalse();
    }

    @Test
    void quotaErrorsBecomeQuotaException() {
        String daily = """
                {"error":{"code":403,"message":"Daily Limit Exceeded","errors":[{"reason":"dailyLimitExceeded"}]}}""";
        assertThatThrownBy(() -> translator.parse(403, daily)).isInstanceOf(TranslationQuotaException.class);
        assertThatThrownBy(() -> translator.parse(429, "")).isInstanceOf(TranslationQuotaException.class);
        String badKey = """
                {"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","errors":[{"reason":"badRequest"}]}}""";
        assertThatThrownBy(() -> translator.parse(400, badKey))
                .isNotInstanceOf(TranslationQuotaException.class)
                .hasMessageContaining("API key not valid");
    }
}
