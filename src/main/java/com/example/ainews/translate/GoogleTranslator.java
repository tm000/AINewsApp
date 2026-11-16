package com.example.ainews.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Google Cloud Translation API (Basic / v2)。API キーで呼ぶ。
 * 毎月 50 万文字まで無料。それを超えると課金されるので、Google Cloud 側で 1 日の上限を設定しておくと安心。
 */
public class GoogleTranslator implements Translator {

    static final String ENDPOINT = "https://translation.googleapis.com/language/translate/v2";

    private final String apiKey;
    private final HttpClient client = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleTranslator(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "Google 翻訳";
    }

    @Override
    public boolean supportsBulk() {
        return true;
    }

    /** 無料枠（月 50 万文字）に収めるため、巡回時の自動翻訳はタイトルだけにする。要約はボタンを押したときに訳す */
    @Override
    public boolean bulkIncludesSummary() {
        return false;
    }

    /** タイトルと要約をまとめて 1 回のリクエストで翻訳する */
    @Override
    public List<Digest> digest(List<DigestRequest> items) throws Exception {
        List<String> texts = new ArrayList<>();
        for (DigestRequest r : items) {
            texts.add(r.title());
            texts.add(r.summary() == null ? "" : r.summary());
        }
        List<String> res = toJapanese(texts, null);
        List<Digest> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String summary = res.get(i * 2 + 1);
            out.add(new Digest(res.get(i * 2), summary == null || summary.isBlank() ? null : summary));
        }
        return out;
    }

    @Override
    public List<String> toJapanese(List<String> texts, String sourceLang) throws Exception {
        // 空文字は送らずに空文字のまま返す（無駄な文字数を数えられないように）
        List<Integer> idx = new ArrayList<>();
        ObjectNode body = mapper.createObjectNode();
        var q = body.putArray("q");
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t != null && !t.isBlank()) {
                q.add(t);
                idx.add(i);
            }
        }
        List<String> out = new ArrayList<>();
        texts.forEach(t -> out.add(""));
        if (idx.isEmpty()) {
            return out;
        }
        body.put("target", "ja");
        body.put("format", "text");
        if (sourceLang != null && !sourceLang.isBlank()) {
            body.put("source", sourceLang.substring(0, 2).toLowerCase(Locale.ROOT));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        List<String> translated = parse(res.statusCode(), res.body());
        for (int i = 0; i < idx.size() && i < translated.size(); i++) {
            out.set(idx.get(i), translated.get(i));
        }
        return out;
    }

    /** レスポンスを読み取る。利用上限のエラーは TranslationQuotaException にする */
    List<String> parse(int status, String json) throws IOException {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (IOException e) {
            root = mapper.createObjectNode();
        }
        if (status != 200) {
            JsonNode error = root.path("error");
            String reason = error.path("errors").path(0).path("reason").asText("");
            String message = error.path("message").asText("");
            String text = (reason + " " + message).toLowerCase(Locale.ROOT);
            if (status == 429 || text.contains("limitexceeded") || text.contains("quota")) {
                throw new TranslationQuotaException("Google 翻訳の利用上限に達しました");
            }
            throw new IOException("Google 翻訳 HTTP " + status + (message.isEmpty() ? "" : ": " + message));
        }
        List<String> out = new ArrayList<>();
        for (JsonNode t : root.path("data").path("translations")) {
            out.add(t.path("translatedText").asText());
        }
        return out;
    }
}
