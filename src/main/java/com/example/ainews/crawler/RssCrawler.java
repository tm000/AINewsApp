package com.example.ainews.crawler;

import com.example.ainews.config.AiNewsProperties.Feed;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.jdom2.Element;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/** RSS / Atom フィードから記事を取得する */
@Component
public class RssCrawler {

    private final HttpFetcher http;

    public RssCrawler(HttpFetcher http) {
        this.http = http;
    }

    public List<FetchedArticle> crawl(Feed feed) throws Exception {
        return parse(feed, http.getBytes(feed.getUrl()));
    }

    public List<FetchedArticle> parse(Feed feed, byte[] xml) throws Exception {
        SyndFeedInput input = new SyndFeedInput();
        input.setAllowDoctypes(false);
        SyndFeed syndFeed;
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(xml))) {
            syndFeed = input.build(reader);
        }
        String feedSite = syndFeed.getLink() != null ? TextUtils.siteOf(syndFeed.getLink()) : TextUtils.siteOf(feed.getUrl());

        List<FetchedArticle> result = new ArrayList<>();
        for (SyndEntry entry : syndFeed.getEntries()) {
            String link = entry.getLink();
            if ((link == null || link.isBlank()) && entry.getUri() != null && entry.getUri().startsWith("http")) {
                link = entry.getUri();
            }
            if (link == null || link.isBlank() || entry.getTitle() == null) {
                continue;
            }
            String title = TextUtils.plainText(entry.getTitle(), 1000);
            String sourceName = feed.getName();
            String sourceSite = feedSite;
            if (feed.isAggregator() && entry.getSource() != null && entry.getSource().getTitle() != null) {
                sourceName = entry.getSource().getTitle().trim();
                if (entry.getSource().getLink() != null) {
                    sourceSite = entry.getSource().getLink();
                }
                // Google ニュースはタイトル末尾に「 - 媒体名」が付くので取り除く
                String suffix = " - " + sourceName;
                if (title != null && title.endsWith(suffix)) {
                    title = title.substring(0, title.length() - suffix.length());
                }
            }

            String html = entryHtml(entry);
            String summary = TextUtils.plainText(html, 400);
            if (summary != null && summary.equals(title)) {
                summary = null;
            }
            String image = findImage(entry, html, link);
            Date date = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
            Instant publishedAt = date != null ? date.toInstant() : Instant.now();
            if (publishedAt.isAfter(Instant.now())) {
                publishedAt = Instant.now();
            }

            if (!matchesKeywords(feed, title, summary)) {
                continue;
            }
            result.add(new FetchedArticle(
                    link.trim(), title, summary, sourceName, sourceSite, feed.getName(), image, publishedAt,
                    TextUtils.detectLanguage(title, feed.getLanguage()), feed.getWeight(), 0, 0, null));
        }
        return result;
    }

    /** フィードに keywords があれば、タイトルか要約にどれかを含む記事だけを通す */
    static boolean matchesKeywords(Feed feed, String title, String summary) {
        List<String> keywords = feed.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        String text = (title == null ? "" : title) + " " + (summary == null ? "" : summary);
        for (String k : keywords) {
            if (k != null && !k.isBlank() && keywordPattern(k).matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.Map<String, Pattern> KEYWORD_PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 英字の語は単語の一部に一致しないようにする（"AI" が "MAIL" や "said" に一致しないように） */
    private static Pattern keywordPattern(String keyword) {
        return KEYWORD_PATTERNS.computeIfAbsent(keyword, k -> {
            String q = Pattern.quote(k.trim());
            return Pattern.compile("(?<![A-Za-z0-9])" + q + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        });
    }

    private static String entryHtml(SyndEntry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            sb.append(entry.getDescription().getValue());
        }
        if (sb.isEmpty()) {
            for (SyndContent c : entry.getContents()) {
                if (c.getValue() != null) {
                    sb.append(c.getValue()).append(' ');
                }
            }
        }
        return sb.toString();
    }

    /** media:content / media:thumbnail / enclosure / 本文中の img の順に画像を探す */
    static String findImage(SyndEntry entry, String html, String baseUrl) {
        for (Element el : entry.getForeignMarkup()) {
            String found = mediaImage(el);
            if (found != null) {
                return found;
            }
        }
        for (SyndEnclosure enc : entry.getEnclosures()) {
            if (enc.getUrl() != null && (enc.getType() == null || enc.getType().startsWith("image"))) {
                return enc.getUrl();
            }
        }
        StringBuilder all = new StringBuilder(html == null ? "" : html);
        for (SyndContent c : entry.getContents()) {
            if (c.getValue() != null) {
                all.append(c.getValue());
            }
        }
        if (!all.isEmpty()) {
            Document doc = Jsoup.parse(all.toString(), baseUrl);
            for (org.jsoup.nodes.Element img : doc.select("img[src]")) {
                String src = img.absUrl("src");
                if (isUsableImage(src, img)) {
                    return src;
                }
            }
        }
        return null;
    }

    private static String mediaImage(Element el) {
        String name = el.getName();
        String ns = el.getNamespaceURI();
        if (ns != null && ns.contains("search.yahoo.com/mrss")) {
            if ("thumbnail".equals(name) && el.getAttributeValue("url") != null) {
                return el.getAttributeValue("url");
            }
            if ("content".equals(name)) {
                String type = el.getAttributeValue("type");
                String medium = el.getAttributeValue("medium");
                String url = el.getAttributeValue("url");
                if (url != null && ("image".equals(medium) || (type != null && type.startsWith("image"))
                        || url.matches("(?i).*\\.(jpe?g|png|webp|gif)(\\?.*)?$"))) {
                    return url;
                }
            }
            if ("group".equals(name)) {
                for (Element child : el.getChildren()) {
                    String found = mediaImage(child);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isUsableImage(String src, org.jsoup.nodes.Element img) {
        if (src == null || !src.startsWith("http")) {
            return false;
        }
        // トラッキングピクセルや小さなアイコンは除外
        String w = img.attr("width");
        String h = img.attr("height");
        if ((w.matches("\\d+") && Integer.parseInt(w) < 50) || (h.matches("\\d+") && Integer.parseInt(h) < 50)) {
            return false;
        }
        String lower = src.toLowerCase();
        return !(lower.contains("feeds.feedburner") || lower.contains("pixel") || lower.contains("emoji")
                || lower.endsWith(".svg"));
    }
}
