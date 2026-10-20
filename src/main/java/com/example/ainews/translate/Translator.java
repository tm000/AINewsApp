package com.example.ainews.translate;

import java.util.List;

/** 翻訳エンジン。実装を差し替えられるようにインターフェースにしている */
public interface Translator {

    String name();

    /** texts を順番どおりに日本語へ翻訳する */
    List<String> toJapanese(List<String> texts, String sourceLang) throws Exception;
}
