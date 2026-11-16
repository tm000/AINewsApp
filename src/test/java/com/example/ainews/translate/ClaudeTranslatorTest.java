package com.example.ainews.translate;

import com.example.ainews.translate.ClaudeTranslator.DigestBatch;
import com.example.ainews.translate.ClaudeTranslator.DigestItem;
import com.example.ainews.translate.Translator.Digest;
import com.example.ainews.translate.Translator.DigestRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeTranslatorTest {

    private final ClaudeTranslator translator = new ClaudeTranslator("test-key", "claude-haiku-5-5");

    @Test
    void buildsRequestWithAllArticles() throws Exception {
        var params = translator.params(List.of(
                new DigestRequest("OpenAI ships a model", "It is fast.", "en"),
                new DigestRequest("EU passes AI Act update", null, "en")));
        String body = params.rawParams()._body().toString();

        assertThat(translator.supportsBulk()).isTrue();
        assertThat(translator.name()).contains("claude-haiku-5-5");
        assertThat(body).contains("OpenAI ships a model").contains("EU passes AI Act update").contains("\"id\" : 1");
        // JSON で結果を返させる（スキーマ付き）・低い effort で費用を抑える
        assertThat(body).contains("json_schema").contains("titleJa").contains("summaryJa").contains("low");
    }

    @Test
    void matchesResultsById() {
        DigestBatch batch = new DigestBatch(List.of(
                new DigestItem(1, " EU が AI 法を改正 ", ""),
                new DigestItem(0, "OpenAI が新モデルを公開", "・高速\n・低価格")));

        List<Digest> out = ClaudeTranslator.toDigests(3, batch);

        assertThat(out.get(0)).isEqualTo(new Digest("OpenAI が新モデルを公開", "・高速\n・低価格"));
        assertThat(out.get(1)).isEqualTo(new Digest("EU が AI 法を改正", null));
        // 返ってこなかった記事は null（次の巡回で再挑戦）
        assertThat(out.get(2)).isNull();
    }
}
