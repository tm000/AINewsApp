package com.example.ainews.e2e;

import com.example.ainews.crawler.FetchedArticle;
import com.example.ainews.repo.ArticleRepository;
import com.example.ainews.service.ArticleIngestService;
import com.example.ainews.translate.FakeTranslatorConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 実際のブラウザ（Chromium）でアプリを操作する E2E テスト。
 * アプリはテスト用のメモリ DB で起動し、ニュースサイトや翻訳 API には一切アクセスしない。
 *
 * <pre>
 * mvn test -Pe2e                        # ヘッドレスで実行
 * mvn test -Pe2e -Dheadless=false       # ブラウザ画面を表示して実行
 * </pre>
 * 失敗したテストのスクリーンショットは target/e2e-screenshots/ に保存される。
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1",
        "ainews.crawl.initial-delay=PT24H"
})
@Import(FakeTranslatorConfig.class)
class BrowserE2ETest {

    /** 1x1 の PNG。記事サムネイルの代わりに返す */
    private static final byte[] PIXEL = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    static Playwright playwright;
    static Browser browser;

    @LocalServerPort int port;
    @Autowired ArticleIngestService ingest;
    @Autowired ArticleRepository repo;

    BrowserContext context;
    Page page;
    boolean failed = true;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(!"false".equals(System.getProperty("headless")));
        // 既にインストール済みの Chrome/Chromium を使いたいときはパスを指定できる
        String exe = System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE");
        if (exe != null && !exe.isBlank()) {
            opts.setExecutablePath(Paths.get(exe));
        }
        browser = playwright.chromium().launch(opts);
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        Instant now = Instant.now();
        ingest.ingest(List.of(
                new FetchedArticle("https://example.com/new", "Brand new AI story", "A fresh summary", "Example",
                        "https://example.com", "Example", "https://img.example.com/a.png", now.minus(Duration.ofMinutes(10)),
                        "en", 1.0, 0, 0, null),
                new FetchedArticle("https://example.com/hot", "Hot AI story", null, "Example",
                        "https://example.com", "Example", null, now.minus(Duration.ofHours(6)), "en", 1.0, 0, 0, null),
                new FetchedArticle("https://example.org/quota", "QUOTA limited story", null, "Other Media",
                        "https://example.org", "Other Media", null, now.minus(Duration.ofHours(8)), "en", 1.0, 0, 0, null),
                new FetchedArticle("https://example.jp/ja", "国内のAIニュース", null, "日本の媒体",
                        "https://example.jp", "日本の媒体", null, now.minus(Duration.ofHours(1)), "ja", 1.0, 0, 0, null)));
        // Hacker News で話題になった記事として、ポイントを足す
        ingest.ingest(List.of(new FetchedArticle("https://example.com/hot", "Hot AI story (HN)", null, "example.com",
                "https://example.com", "Hacker News", null, now.minus(Duration.ofHours(6)), "en", 1.0, 800, 300,
                "https://news.ycombinator.com/item?id=1")));
        ingest.rescore(Duration.ofDays(30));

        context = browser.newContext(new Browser.NewContextOptions().setLocale("ja-JP"));
        context.setDefaultTimeout(10_000);
        // 外部サイトへは出ていかない。サムネイル画像だけ偽の PNG を返す
        context.route(Pattern.compile("^https?://(?!localhost|127\\.0\\.0\\.1).*"), route -> {
            if (route.request().url().startsWith("https://img.example.com/")) {
                route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(200).setContentType("image/png").setBodyBytes(PIXEL));
            } else {
                route.abort();
            }
        });
        page = context.newPage();
    }

    @AfterEach
    void tearDown(TestInfo info) throws Exception {
        if (failed && page != null) {
            Path dir = Paths.get("target", "e2e-screenshots");
            Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions().setFullPage(true)
                    .setPath(dir.resolve(info.getTestMethod().map(m -> m.getName()).orElse("test") + ".png")));
        }
        if (context != null) {
            context.close();
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Locator titles() {
        return page.locator(".card .title .t-orig");
    }

    private Locator card(String title) {
        return page.locator(".card").filter(new Locator.FilterOptions().setHasText(title));
    }

    @Test
    @DisplayName("新着順と注目度順を切り替えられる")
    void switchesBetweenNewestAndPopular() {
        page.navigate(url("/"));
        assertThat(page).hasTitle("AIニュース");
        assertThat(titles()).hasCount(4);
        assertThat(titles().first()).hasText("Brand new AI story");
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("新着順"))).hasClass(Pattern.compile("active"));

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("注目度順")).click();
        assertThat(page).hasURL(Pattern.compile("sort=popular"));
        assertThat(titles().first()).hasText("Hot AI story");
        assertThat(page.locator(".card .rank").first()).hasText("1");
        // Hacker News の反響が表示される
        assertThat(card("Hot AI story").locator(".pill").first()).containsText("800");
        failed = false;
    }

    @Test
    @DisplayName("出典とサムネイルが表示される")
    void showsSourceAndThumbnail() {
        page.navigate(url("/"));
        Locator fresh = card("Brand new AI story");
        Locator source = fresh.locator("a.source");
        assertThat(source).hasText("Example");
        assertThat(source).hasAttribute("href", "https://example.com");
        assertThat(fresh.locator(".title a")).hasAttribute("href", "https://example.com/new");
        assertThat(fresh.locator(".title a")).hasAttribute("target", "_blank");

        Locator img = fresh.locator(".thumb img");
        assertThat(img).isVisible();
        assertThat(img).hasAttribute("src", "https://img.example.com/a.png");
        // 画像の無い記事にはサムネイル枠を出さない
        assertThat(card("Hot AI story").locator(".thumb")).hasCount(0);
        failed = false;
    }

    @Test
    @DisplayName("キーワードと言語で絞り込める")
    void filtersByKeywordAndLanguage() {
        page.navigate(url("/"));
        page.getByLabel("言語").selectOption("ja");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("絞り込み")).click();
        assertThat(page).hasURL(Pattern.compile("lang=ja"));
        assertThat(titles()).hasCount(1);
        assertThat(titles().first()).hasText("国内のAIニュース");

        page.getByLabel("言語").selectOption("");
        page.getByLabel("キーワード").fill("Hot");
        page.getByLabel("キーワード").press("Enter");
        assertThat(titles()).hasCount(1);
        assertThat(titles().first()).hasText("Hot AI story");

        page.getByLabel("キーワード").fill("存在しないキーワード");
        page.getByLabel("キーワード").press("Enter");
        assertThat(page.locator(".empty")).containsText("条件に合う記事がありません");
        failed = false;
    }

    @Test
    @DisplayName("海外記事を日本語に翻訳して原文に戻せる")
    void translatesForeignArticleAndBack() {
        page.navigate(url("/"));
        Locator fresh = card("Brand new AI story");
        // 日本語の記事には翻訳ボタンが無い
        assertThat(card("国内のAIニュース").locator("button.translate")).hasCount(0);

        fresh.locator("button.translate").click();
        assertThat(fresh.locator(".t-ja")).isVisible();
        assertThat(fresh.locator(".t-ja")).hasText(FakeTranslatorConfig.PREFIX + "Brand new AI story");
        assertThat(fresh.locator(".s-ja")).hasText(FakeTranslatorConfig.PREFIX + "A fresh summary");
        assertThat(fresh.locator(".t-orig")).isHidden();
        assertThat(fresh.locator("button.translate")).hasText("原文を表示");

        fresh.locator("button.translate").click();
        assertThat(fresh.locator(".t-orig")).isVisible();
        assertThat(fresh.locator(".t-ja")).isHidden();
        assertThat(fresh.locator("button.translate")).hasText("日本語に翻訳");

        // 翻訳結果は保存されるので、再読み込み後も日本語で表示できる
        page.reload();
        card("Brand new AI story").locator("button.translate").click();
        assertThat(card("Brand new AI story").locator(".t-ja")).isVisible();
        failed = false;
    }

    @Test
    @DisplayName("翻訳の利用上限に達したらメッセージを表示する")
    void showsMessageWhenTranslationQuotaExceeded() {
        page.navigate(url("/"));
        Locator quota = card("QUOTA limited story");
        quota.locator("button.translate").click();
        assertThat(page.locator("#toast")).isVisible();
        assertThat(page.locator("#toast")).containsText("利用上限");
        assertThat(quota.locator(".t-orig")).isVisible();
        assertThat(quota.locator("button.translate")).hasText("日本語に翻訳");
        failed = false;
    }

    @Test
    @DisplayName("自動翻訳スイッチで海外記事をまとめて日本語表示する")
    void autoTranslateSwitchTranslatesAll() {
        page.navigate(url("/"));
        page.getByLabel("海外記事を日本語で表示").check();
        assertThat(card("Brand new AI story").locator(".t-ja")).isVisible();
        assertThat(card("Hot AI story").locator(".t-ja")).isVisible();
        // 利用上限に当たったらスイッチは自動でオフになる
        assertThat(page.locator("#toast")).containsText("利用上限");
        assertThat(page.getByLabel("海外記事を日本語で表示")).not().isChecked();
        failed = false;
    }

    @Test
    @DisplayName("取得元ページを開ける")
    void opensSourcesPage() {
        page.navigate(url("/"));
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("取得元の一覧と状態")).click();
        assertThat(page).hasURL(Pattern.compile("/sources$"));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("取得元の一覧と状態"))).isVisible();
        assertThat(page.locator("body")).containsText("翻訳エンジン: テスト用");
        page.getByText("← 記事一覧へ戻る").click();
        assertThat(titles()).hasCount(4);
        failed = false;
    }

    @Test
    @DisplayName("スマホ幅でも横スクロールしない")
    void noHorizontalScrollOnPhone() {
        Page mobile = context.newPage();
        mobile.setViewportSize(390, 844);
        mobile.navigate(url("/"));
        assertThat(mobile.locator(".card").first()).isVisible();
        Object overflow = mobile.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth");
        org.assertj.core.api.Assertions.assertThat(((Number) overflow).intValue()).isLessThanOrEqualTo(0);
        failed = false;
    }
}
