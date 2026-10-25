# TextEditor for Android

日本語UIのAndroid向けテキストエディターです。現在までの実装をまとめたソースリポジトリです。

## 主な機能

- UTF-8テキストの新規作成、開く、保存、名前を付けて保存、再読み込み
- 検索・置換、正規表現、元に戻す／やり直す、本文クリア
- 最近使用したファイルの履歴と、通常起動時の前回ファイル再オープン
- 行番号、折り返し、空白・タブ・改行記号、表示倍率、任意のピンチ拡大
- ライト／ダークテーマ、自動保存、専用の設定・ヘルプページ
- 実際の本文レイアウトに追従する行番号と、縦フリックの慣性スクロール

ファイル操作にはStorage Access Frameworkを使用します。端末内ファイルへの広範なアクセスは、利用者がAndroidの設定画面で明示的に許可します。起動時の再オープンは保存済みファイルの読み込みであり、未保存本文の永続バックアップではありません。

## ビルド

- JDK 17以上
- Android SDK（compileSdk 34、minSdk 24）
- Gradle Wrapper付属

`local.properties`に各環境の`sdk.dir`を指定するか、Android SDKの環境変数を設定してください。端末固有の設定はGit管理対象外です。

この開発環境の`gradle.properties`にはTermux用の`android.aapt2FromMavenOverride`が設定されています。PCなど他の環境ではこの行を削除するか、その環境で使用できるaapt2へ変更してください。

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

デバッグAPK: `app/build/outputs/apk/debug/app-debug.apk`

applicationId: `com.metaapplication.generated.app4111941c17b94b1b87f96b8cc4549ebb`

## 設計・検証状況

機能ごとの設計、制約、手動確認項目は`docs/`に記録しています。ビルド・単体テストと実機の表示／操作確認は別です。実機で未確認の項目は各資料を参照してください。

Git履歴のない作業フォルダーからの初回登録のため、これまでの開発結果は1つの初期コミットにまとめています。キャッシュ、APK、端末設定、添付スクリーンショットは含めません。

## アイコン素材

UIアイコンはGoogle Material Design Icons（Apache-2.0）を利用しています。原本とライセンスは`docs/icon-sources/`、ユーザー提供のアプリアイコン原本は`docs/app-icon.svg`にあります。
