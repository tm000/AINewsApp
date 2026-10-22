# AIニュース

世界中の最新 AI ニュースを自動で集めて、**新着順**・**注目度順**で並べて読める Web アプリです（Java 21 / Spring Boot 3）。

- 各記事に**出典（媒体名）**を表示し、タップすると出典元のページが開きます
- 記事に画像があれば**サムネイル**を表示（RSS の画像 → 無ければ記事ページの og:image）
- 海外記事は**日本語に翻訳**できます（記事ごとのボタン、または「海外記事を日本語で表示」スイッチ）
- キーワード・期間・言語（日本語/海外）・出典で絞り込み
- スマホ表示に最適化、ダークモード対応

## 取得元

`src/main/resources/application.yml` の `ainews.feeds` で追加・削除できます。

| 種類 | 取得元 |
| --- | --- |
| 海外メディア | TechCrunch, The Verge, VentureBeat, MIT Technology Review, Ars Technica, WIRED, The Guardian |
| 企業ブログ | OpenAI, Google AI Blog, Hugging Face |
| 国内 | ITmedia AI+, Google ニュース（「生成AI」検索） |
| 反響 | Hacker News（Algolia API）のポイント・コメント数 |

30 分ごとに自動で巡回します（画面右上の「今すぐ更新」で手動実行も可）。30 日より古い記事は自動で削除します。

## 注目度の計算

```
基礎点 = HNポイント + HNコメント数×2 + 同じ話題を報じた他媒体の数×15 + 媒体の重み×10
注目度 = 基礎点 / (経過時間[時間] + 2)^1.5
```

Hacker News のランキング式と同じ考え方で、反響が大きいほど上がり、時間が経つほど下がります。
「同じ話題を報じた他媒体の数」は 48 時間以内の記事のタイトルの単語の重なりで判定しています。

## 翻訳

| 設定 | 内容 |
| --- | --- |
| 何も設定しない | **MyMemory**（無料・登録不要、1 日 5,000 文字まで） |
| `MYMEMORY_EMAIL` | MyMemory の無料枠が 1 日 50,000 文字に増えます |
| `DEEPL_API_KEY` | **DeepL** を使います（DeepL API Free は月 50 万文字まで無料、要登録） |
| `TRANSLATE_PROVIDER=none` | 翻訳を無効化 |

翻訳結果は DB に保存されるので、同じ記事を何度表示しても翻訳は 1 回だけです。

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
| POST | `/api/crawl` | 今すぐ巡回 |
| GET | `/api/status` | 巡回状況・取得元ごとのエラー |

## テスト

```bash
mvn test
```
