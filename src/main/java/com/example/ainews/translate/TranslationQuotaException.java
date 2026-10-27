package com.example.ainews.translate;

import java.io.IOException;

/** 翻訳サービスの利用上限（HTTP 429 や無料枠の使い切り）に達した */
public class TranslationQuotaException extends IOException {

    public TranslationQuotaException(String message) {
        super(message);
    }
}
