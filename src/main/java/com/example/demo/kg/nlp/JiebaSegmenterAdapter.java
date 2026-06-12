package com.example.demo.kg.nlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Jieba adapter.
 *
 * It uses jieba-analysis through reflection when the dependency is available.
 * If the dependency is not present, it falls back to a deterministic CJK/Latin tokenizer
 * so the project can still compile and run before the Maven dependency is added.
 *
 * Recommended Maven dependency:
 *   io.github.yrjyrj123:jieba-analysis:1.0.3
 * or legacy:
 *   com.huaban:jieba-analysis:1.0.2
 */
@Component
public class JiebaSegmenterAdapter {

    private static final Logger log = LoggerFactory.getLogger(JiebaSegmenterAdapter.class);

    private final Object segmenter;
    private final Method processMethod;
    private final Object indexMode;
    private final boolean jiebaAvailable;

    public JiebaSegmenterAdapter() {
        Object seg = null;
        Method method = null;
        Object mode = null;
        boolean ok = false;
        try {
            Class<?> segClass = Class.forName("com.huaban.analysis.jieba.JiebaSegmenter");
            Class<?> segModeClass = Class.forName("com.huaban.analysis.jieba.JiebaSegmenter$SegMode");
            seg = segClass.getConstructor().newInstance();
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumMode = Enum.valueOf((Class<? extends Enum>) segModeClass.asSubclass(Enum.class), "INDEX");
            mode = enumMode;
            method = segClass.getMethod("process", String.class, segModeClass);
            ok = true;
        } catch (Exception e) {
            log.info("jieba-analysis is not available, fallback tokenizer will be used: {}", e.getClass().getSimpleName());
        }
        this.segmenter = seg;
        this.processMethod = method;
        this.indexMode = mode;
        this.jiebaAvailable = ok;
    }

    public boolean isJiebaAvailable() {
        return jiebaAvailable;
    }

    public List<String> segment(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (jiebaAvailable) {
            try {
                Object result = processMethod.invoke(segmenter, text, indexMode);
                if (result instanceof List<?> tokens) {
                    Set<String> words = new LinkedHashSet<>();
                    for (Object token : tokens) {
                        String word = readWord(token);
                        if (word != null && !word.isBlank()) {
                            words.add(word.trim());
                        }
                    }
                    if (!words.isEmpty()) {
                        return new ArrayList<>(words);
                    }
                }
            } catch (Exception e) {
                log.warn("jieba segmentation failed, fallback tokenizer will be used: {}", e.getMessage());
            }
        }
        return fallbackSegment(text);
    }

    private String readWord(Object token) {
        try {
            Field wordField = token.getClass().getField("word");
            Object value = wordField.get(token);
            return value == null ? null : value.toString();
        } catch (Exception ignored) {
            String value = token.toString();
            int comma = value.indexOf(',');
            return comma > 0 ? value.substring(0, comma).replace("[", "").trim() : value.trim();
        }
    }

    private List<String> fallbackSegment(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        StringBuilder buf = new StringBuilder();
        Character.UnicodeScript lastScript = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                flush(tokens, buf);
                lastScript = null;
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(c);
            if (buf.length() > 0 && lastScript != null && script != lastScript && !(isAsciiLetterOrDigit(c) && isAsciiLetterOrDigit(buf.charAt(buf.length() - 1)))) {
                flush(tokens, buf);
            }
            buf.append(c);
            lastScript = script;
        }
        flush(tokens, buf);
        return new ArrayList<>(tokens);
    }

    private boolean isPunctuation(char c) {
        int type = Character.getType(c);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || ",.;:!?，。；：！？、（）()[]{}<>《》/\\|".indexOf(c) >= 0;
    }

    private boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private void flush(Set<String> tokens, StringBuilder buf) {
        if (buf.length() > 0) {
            String token = buf.toString().trim();
            if (!token.isBlank()) {
                tokens.add(token);
            }
            buf.setLength(0);
        }
    }
}
