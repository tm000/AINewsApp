package com.example.ainews.translate;

import java.util.ArrayList;
import java.util.List;

/** 翻訳エンジン。実装を差し替えられるようにインターフェースにしている */
public interface Translator {

    /** 翻訳・要約したい記事 */
    record DigestRequest(String title, String summary, String language) {}

    /** 日本語のタイトルと要約（要約が作れないときは null） */
    record Digest(String titleJa, String summaryJa) {}

    String name();

    /** texts を順番どおりに日本語へ翻訳する */
    List<String> toJapanese(List<String> texts, String sourceLang) throws Exception;

    /**
     * 巡回のたびに新着記事をまとめて翻訳してよいか。
     * 無料枠の小さいエンジン（MyMemory）は false にして、ボタンを押したときだけ翻訳する。
     */
    default boolean supportsBulk() {
        return false;
    }

    /**
     * 記事のタイトルを日本語にし、要約を日本語にする。結果は items と同じ順番・同じ件数で返す。
     * 既定はタイトルと要約を別々に翻訳する。要約の翻訳だけ失敗してもタイトルの訳は返す
     * （無料枠の消費を抑え、上限に当たっても何か表示できるようにするため）。
     */
    default List<Digest> digest(List<DigestRequest> items) throws Exception {
        List<Digest> out = new ArrayList<>();
        for (DigestRequest r : items) {
            String title = toJapanese(List.of(r.title()), r.language()).get(0);
            String summary = null;
            if (r.summary() != null) {
                try {
                    summary = toJapanese(List.of(r.summary()), r.language()).get(0);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(Translator.class)
                            .warn("summary translation skipped: {}", e.toString());
                }
            }
            out.add(new Digest(title, summary));
        }
        return out;
    }
}
