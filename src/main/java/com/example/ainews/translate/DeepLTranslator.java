package com.example.ainews.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DeepL API。キーが ":fx" で終わる場合は無料版（月 50 万文字まで無料）のエンドポイントを使う。
 */
public class DeepLTranslator implements Translator {

    private final String apiKey;
    private final String endpoint;
    private final HttpClient client = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepLTranslator(String apiKey) {
        this.apiKey = apiKey;
        this.endpoint = apiKey.endsWith(":fx")
                ? "https://api-free.deepl.com/v2/translate"
                : "https://api.deepl.com/v2/translate";
    }

    @Override
    public String name() {
        return "DeepL";
    }

    @Override
    public List<String> toJapanese(List<String> texts, String sourceLang) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        var arr = body.putArray("text");
        texts.forEach(t -> arr.add(t == null ? "" : t));
        body.put("target_lang", "JA");
        if (sourceLang != null && !sourceLang.isBlank()) {
            body.put("source_lang", sourceLang.substring(0, 2).toUpperCase(Locale.ROOT));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "DeepL-Auth-Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429 || res.statusCode() == 456) {
            throw new TranslationQuotaException("DeepL の利用上限に達しました");
        }
        if (res.statusCode() != 200) {
            throw new IOException("DeepL HTTP " + res.statusCode());
        }
        List<String> out = new ArrayList<>();
        for (JsonNode t : mapper.readTree(res.body()).path("translations")) {
            out.add(t.path("text").asText());
        }
        return out;
    }
}
