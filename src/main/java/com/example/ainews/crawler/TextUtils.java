package com.example.ainews.crawler;

import org.jsoup.Jsoup;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TextUtils {

    private static final Pattern TRACKING_PARAM = Pattern.compile("^(utm_[a-z]+|fbclid|gclid|ref|cmpid|mc_cid|mc_eid)=", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAPANESE = Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}]");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "of", "to", "in", "on", "for", "and", "or", "is", "are", "with", "by", "at",
            "as", "from", "its", "it", "that", "this", "be", "has", "have", "how", "why", "what", "new", "says");

    private TextUtils() {}

    /** HTML を除去してプレーンテキストにし、最大長で切る */
    public static String plainText(String html, int maxLength) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = Jsoup.parse(html).text().replaceAll("\\s+", " ").trim();
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength - 1) + "…";
        }
        return text.isEmpty() ? null : text;
    }

    /** トラッキング用パラメータや末尾スラッシュを除いて、重複判定しやすい URL にする */
    public static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String query = uri.getRawQuery();
            if (query != null) {
                query = Arrays.stream(query.split("&"))
                        .filter(p -> !p.isEmpty() && !TRACKING_PARAM.matcher(p).find())
                        .collect(Collectors.joining("&"));
                if (query.isEmpty()) {
                    query = null;
                }
            }
            String path = uri.getRawPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            return scheme + "://" + host + port + (path == null ? "" : path) + (query == null ? "" : "?" + query);
        } catch (IllegalArgumentException e) {
            return url.trim();
        }
    }

    public static String siteOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    public static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host != null && host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception e) {
            return null;
        }
    }

    /** ひらがな・カタカナを含めば日本語とみなす */
    public static String detectLanguage(String text, String fallback) {
        if (text != null && JAPANESE.matcher(text).find()) {
            return "ja";
        }
        return fallback;
    }

    /** タイトルの類似度判定用の単語集合 */
    public static Set<String> titleTokens(String title) {
        if (title == null) {
            return Set.of();
        }
        return Arrays.stream(title.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> w.length() > 1 && !STOP_WORDS.contains(w))
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }
}
