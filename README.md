# AIニュース

世界中の最新 AI ニュースを自動で集めて、**新着順**・**注目度順**で並べて読める Web アプリです（Java 21 / Spring Boot 3）。

- 各記事に**出典（媒体名）**を表示し、タップすると出典元のページが開きます
- 記事に画像があれば**サムネイル**を表示（RSS の画像 → 無ければ記事ページの og:image）
- 海外記事は**日本語に翻訳**できます（記事ごとのボタン、または「海外記事を日本語で表示」スイッチ）
  - Claude API を設定すると、巡回のたびに新着の海外記事を**自動で翻訳し、日本語の 3 行要約**を付けます
- **同じ話題の記事は 1 枚のカードにまとめ**、「同じ話題を他 N 件の記事が報道」から他の出典を開けます
- キーワード・期間・言語（日本語/海外）・出典で絞り込み
- スマホ表示に最適化、ダークモード対応

## 取得元

`src/main/resources/application.yml` の `ainews.feeds` で追加・削除できます。

| 種類 | 取得元 |
| --- | --- |
| 海外メディア | TechCrunch, The Verge, VentureBeat, MIT Technology Review, Ars Technica, WIRED, The Guardian, Google News（Anthropic・Meta AI など） |
| 企業ブログ | OpenAI, Google AI Blog, Google DeepMind, Microsoft AI Blog, NVIDIA Blog, Hugging Face |
| コミュニティ | Reddit（r/MachineLearning, r/LocalLLaMA の 1 日の上位） |
| 国内 | ITmedia AI+, AINOW, 日経クロステック, Publickey, GIGAZINE, Google ニュース（「生成AI」検索） |

AI 専門でない媒体（NVIDIA Blog・日経クロステック・Publickey・GIGAZINE）は、フィードの `keywords` に挙げた語
（AI・生成AI・LLM など）をタイトルか要約に含む記事だけを取り込みます。
| 反響 | Hacker News（Algolia API）のポイント・コメント数 |

30 分ごとに自動で巡回します（画面右上の「今すぐ更新」で手動実行も可。ただし前回の巡回開始から 5 分間は受け付けません）。30 日より古い記事は自動で削除します。

## 注目度の計算

```
基礎点 = HNポイント + HNコメント数×2 + 同じ話題を報じた他媒体の数×15 + 媒体の重み×10
注目度 = 基礎点 / (経過時間[時間] + 2)^1.5
```

Hacker News のランキング式と同じ考え方で、反響が大きいほど上がり、時間が経つほど下がります。
「同じ話題を報じた他媒体の数」は 48 時間以内の記事のタイトルの単語の重なりで判定しています（日本語は 2 文字ずつに区切って比較）。
同じ話題と判定した記事は 1 つにまとめ、注目度が一番高い記事を代表として一覧に出します。
出典・言語・キーワードで絞り込んだときは、まとめずに条件に合う記事をすべて出します。

## 翻訳

| 設定 | 内容 |
| --- | --- |
| `ANTHROPIC_API_KEY` | **Claude（おすすめ）**。タイトルの翻訳と日本語の 3 行要約を作り、巡回のたびに新着の海外記事を自動で処理します。既定のモデルは Claude Haiku 5.5（`CLAUDE_MODEL` で変更可） |
| 何も設定しない | **MyMemory**（無料・登録不要、1 日 5,000 文字まで） |
| `MYMEMORY_EMAIL` | MyMemory の無料枠が 1 日 50,000 文字に増えます |
| `DEEPL_API_KEY` | **DeepL** を使います（DeepL API Free は月 50 万文字まで無料、要登録）。巡回時の自動翻訳もします（要約は原文の翻訳） |
| `TRANSLATE_PROVIDER=none` | 翻訳を無効化 |

複数設定したときは Claude → DeepL → MyMemory の順に使います（`TRANSLATE_PROVIDER=claude|deepl|mymemory` で固定も可）。
翻訳結果は DB に保存されるので、同じ記事を何度表示しても翻訳は 1 回だけです。

Claude / DeepL のときは、巡回のたびに未翻訳の海外記事を新しい順に最大 60 件（`TRANSLATE_AUTO_LIMIT`）、
10 件ずつまとめて翻訳します。Claude Haiku 5.5 なら 1 記事あたりの費用は 0.1 円にもなりません。

## 起動方法

```bash
# Java 21 と Maven が必要
mvn spring-boot:run
# → http://localhost:8080
```

Docker の場合:

```bash
docker build -t ai-news .
docker run -p 8080:8080 -v $(pwd)/data:/app/data ai-news
```

データは `./data/`（H2 データベース）に保存されます。

### スマホから使えるように公開する（Render・無料）

1. https://render.com に GitHub アカウントでログイン
2. 「New +」→「Blueprint」→ このリポジトリを選ぶ（`render.yaml` が自動で読まれます）
3. 数分で `https://ai-news-xxxx.onrender.com` のような URL が発行されるので、スマホで開く

無料プランは 15 分アクセスがないと停止し、次のアクセスで 1 分ほどかけて再起動します。
データベースは再起動で消えますが、起動後に自動で再取得します。

## API

| メソッド | パス | 内容 |
| --- | --- | --- |
| GET | `/api/articles?sort=new\|popular&period=day\|three_days\|week\|all&lang=ja\|foreign&source=&q=&page=&size=` | 記事一覧（JSON） |
| POST | `/api/articles/{id}/translate` | 記事を日本語に翻訳 |
| POST | `/api/crawl` | 今すぐ巡回（前回の巡回開始から 5 分以内は 429） |
| GET | `/api/status` | 巡回状況・取得元ごとのエラー |

## テスト

```bash
mvn test          # 単体テスト・結合テスト（ブラウザ不要）
mvn test -Pe2e    # Playwright による E2E テスト（実際のブラウザで画面を操作）
```

E2E テスト（`src/test/java/com/example/ainews/e2e/`）はアプリをテスト用のメモリ DB で起動し、
ヘッドレスの Chromium で次の操作を確認します。ニュースサイトや翻訳 API には接続しません（翻訳はテスト用の偽エンジン）。

- 新着順 ⇔ 注目度順の切り替えと並び順
- 出典リンクとサムネイルの表示
- キーワード・言語での絞り込み
- 記事ごとの翻訳と原文への切り替え、翻訳の利用上限エラーの表示、自動翻訳スイッチ
- 取得元ページへの移動
- スマホ幅（390px）で横スクロールが出ないこと

初回はブラウザ（Chromium）が自動でダウンロードされます。

- `-Dheadless=false` を付けるとブラウザ画面を表示しながら実行します
- 失敗したテストのスクリーンショットは `target/e2e-screenshots/` に保存されます
- 環境変数 `PLAYWRIGHT_CHROMIUM_EXECUTABLE` でインストール済みの Chromium / Chrome を使えます

GitHub Actions（`.github/workflows/test.yml`）で、push と Pull Request のたびに両方のテストが自動で実行されます。
