package com.example.ainews.translate;

import com.example.ainews.crawler.HttpFetcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MyMemory 翻訳 API（無料・キー不要）。
 * 1 リクエスト 500 バイトまでなので文単位で分割して送る。
 * 無料枠は 1 日 5,000 文字（メールアドレスを渡すと 50,000 文字）。
 */
public class MyMemoryTranslator implements Translator {

    private static final String API = "https://api.mymemory.translated.net/get";
    private static final int MAX_CHUNK = 450;

    private final HttpFetcher http;
    private final String email;
    private final ObjectMapper mapper = new ObjectMapper();

    public MyMemoryTranslator(HttpFetcher http, String email) {
        this.http = http;
        this.email = email;
    }

    @Override
    public String name() {
        return "MyMemory";
    }

    @Override
    public List<String> toJapanese(List<String> texts, String sourceLang) throws Exception {
        List<String> out = new ArrayList<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                out.add(text);
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (String chunk : chunks(text)) {
                sb.append(translateChunk(chunk, sourceLang == null ? "en" : sourceLang));
            }
            out.add(sb.toString());
        }
        return out;
    }

    private String translateChunk(String chunk, String sourceLang) throws Exception {
        String url = API + "?q=" + URLEncoder.encode(chunk, StandardCharsets.UTF_8)
                + "&langpair=" + URLEncoder.encode(sourceLang + "|ja", StandardCharsets.UTF_8)
                + (email == null || email.isBlank() ? "" : "&de=" + URLEncoder.encode(email, StandardCharsets.UTF_8));
        String body;
        try {
            body = http.getString(url);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP 429")) {
                throw new TranslationQuotaException("MyMemory の利用上限に達しました");
            }
            throw e;
        }
        JsonNode root = mapper.readTree(body);
        int status = root.path("responseStatus").asInt(200);
        String translated = root.path("responseData").path("translatedText").asText(null);
        String details = root.path("responseDetails").asText("");
        if (status == 429 || details.toUpperCase().contains("USED ALL AVAILABLE FREE TRANSLATIONS")) {
            throw new TranslationQuotaException("MyMemory の無料枠（1日分）を使い切りました");
        }
        if (status != 200 || translated == null) {
            throw new IOException("MyMemory: " + (details.isEmpty() ? "error " + status : details));
        }
        // 上限到達時に原文がそのまま「翻訳結果」として返ることがあるため弾く
        if (translated.toUpperCase().contains("MYMEMORY WARNING")) {
            throw new TranslationQuotaException("MyMemory の無料枠（1日分）を使い切りました");
        }
        return translated;
    }

    /** 文の区切りでなるべく MAX_CHUNK 文字以内に分ける */
    static List<String> chunks(String text) {
        List<String> result = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?。！？])\\s+");
        StringBuilder cur = new StringBuilder();
        for (String s : sentences) {
            while (s.length() > MAX_CHUNK) {
                if (!cur.isEmpty()) {
                    result.add(cur.toString());
                    cur.setLength(0);
                }
                int cut = s.lastIndexOf(' ', MAX_CHUNK);
                if (cut <= 0) {
                    cut = MAX_CHUNK;
                }
                result.add(s.substring(0, cut));
                s = s.substring(cut).trim();
            }
            if (cur.length() + s.length() + 1 > MAX_CHUNK && !cur.isEmpty()) {
                result.add(cur.toString());
                cur.setLength(0);
            }
            if (!cur.isEmpty()) {
                cur.append(' ');
            }
            cur.append(s);
        }
        if (!cur.isEmpty()) {
            result.add(cur.toString());
        }
        return result;
    }
}
