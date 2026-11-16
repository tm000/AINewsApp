package com.example.ainews.translate;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude API で翻訳・要約する。海外記事はタイトルを日本語にし、要約は日本語の 3 行要約にする。
 * 複数の記事を 1 回のリクエストでまとめて処理するので、記事 1 件あたりの費用はごくわずか。
 */
public class ClaudeTranslator implements Translator {

    static final String SYSTEM_PROMPT = """
            あなたは AI 分野に詳しい日本のニュース編集者です。
            海外の AI ニュースを日本の読者向けに、自然で正確な日本語にします。
            - titleJa: 記事タイトルの日本語訳。見出しとして簡潔に。製品名・企業名・人名は一般的な表記（例: OpenAI、Google、GPT-5）のまま。
            - summaryJa: 与えられた要約の内容だけを使い、記事のポイントを日本語で最大 3 行にまとめる。各行は「・」で始め、40 字程度まで。行は改行で区切る。
              要約が空、または中身が無い（「続きを読む」だけなど）ときは空文字にする。与えられていない事実を付け足さない。
            - 入力の id をそのまま返し、すべての記事について 1 件ずつ返す。
            """;

    /** Claude に返してもらう 1 記事分の結果 */
    public record DigestItem(
            @JsonPropertyDescription("入力の id") int id,
            @JsonPropertyDescription("タイトルの日本語訳") String titleJa,
            @JsonPropertyDescription("日本語の 3 行要約（「・」始まりの行を改行で区切る）。要約が無ければ空文字") String summaryJa) {}

    public record DigestBatch(List<DigestItem> items) {}

    private final AnthropicClient client;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeTranslator(String apiKey, String model) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
    }

    @Override
    public String name() {
        return "Claude (" + model + ")";
    }

    @Override
    public boolean supportsBulk() {
        return true;
    }

    /** 単純な翻訳。要約なしの記事として digest を呼ぶ */
    @Override
    public List<String> toJapanese(List<String> texts, String sourceLang) throws Exception {
        List<DigestRequest> reqs = texts.stream().map(t -> new DigestRequest(t, null, sourceLang)).toList();
        return digest(reqs).stream().map(Digest::titleJa).toList();
    }

    @Override
    public List<Digest> digest(List<DigestRequest> items) throws Exception {
        if (items.isEmpty()) {
            return List.of();
        }
        StructuredMessageCreateParams<DigestBatch> params = params(items);
        DigestBatch batch;
        try {
            batch = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new IOException("Claude から結果が返りませんでした"));
        } catch (RateLimitException e) {
            throw new TranslationQuotaException("Claude API の利用上限に達しました");
        } catch (AnthropicServiceException e) {
            throw new IOException("Claude API HTTP " + e.statusCode() + ": " + e.getMessage(), e);
        }
        return toDigests(items.size(), batch);
    }

    StructuredMessageCreateParams<DigestBatch> params(List<DigestRequest> items) throws IOException {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(Math.min(16000L, 800L + items.size() * 400L))
                .system(SYSTEM_PROMPT)
                .outputConfig(StructuredOutputConfig.<DigestBatch>builder()
                        .format(DigestBatch.class)
                        .effort(OutputConfig.Effort.LOW)
                        .build())
                .addUserMessage(buildUserMessage(items))
                .build();
    }

    String buildUserMessage(List<DigestRequest> items) throws IOException {
        ArrayNode arr = mapper.createArrayNode();
        for (int i = 0; i < items.size(); i++) {
            DigestRequest r = items.get(i);
            ObjectNode o = arr.addObject();
            o.put("id", i);
            o.put("title", r.title());
            o.put("summary", r.summary() == null ? "" : r.summary());
        }
        return "次の記事を日本語にしてください。\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr);
    }

    /** id で入力と突き合わせる。返ってこなかった記事は null（次の巡回で再挑戦する） */
    static List<Digest> toDigests(int size, DigestBatch batch) {
        Map<Integer, DigestItem> byId = new HashMap<>();
        if (batch != null && batch.items() != null) {
            for (DigestItem item : batch.items()) {
                byId.putIfAbsent(item.id(), item);
            }
        }
        List<Digest> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            DigestItem item = byId.get(i);
            if (item == null || item.titleJa() == null || item.titleJa().isBlank()) {
                out.add(null);
            } else {
                String summary = item.summaryJa() == null || item.summaryJa().isBlank() ? null : item.summaryJa().strip();
                out.add(new Digest(item.titleJa().strip(), summary));
            }
        }
        return out;
    }
}
