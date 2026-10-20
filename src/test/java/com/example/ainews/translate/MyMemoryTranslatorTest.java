package com.example.ainews.translate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyMemoryTranslatorTest {

    @Test
    void splitsLongTextIntoChunks() {
        String sentence = "This is a fairly long sentence about artificial intelligence and its many uses. ";
        String text = sentence.repeat(20).trim();
        List<String> chunks = MyMemoryTranslator.chunks(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(450));
        assertThat(String.join(" ", chunks)).isEqualTo(text);
    }
}
