# Branding

- アプリ表示名およびナビゲーションカードのタイトルは `TextEditor`。
- ランチャーの通常アイコンと丸型アイコンは、ユーザー提供の `docs/app-icon.svg` を使用。
- AndroidはSVGをランチャーリソースとして直接利用できないため、形状・色・透明度・回転を保持したVectorDrawable `res/drawable/app_icon.xml` に変換。Manifestから両アイコンとして参照する。
- アイコンはユーザー提供素材のため、外部素材ライセンスの追加はない。
