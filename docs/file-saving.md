# 新規表示名と名前を付けて保存

- 新規作成時の表示名と保存候補名は、日本語「新規ファイル」、英語「New file」。アプリから.txtを付けない。既存ファイルは変更しない。
- 利用者報告: main.pyがmain.py.txtになる。以前のCreateDocument("*/*")だけの対策では不十分だった。現在は「名前を付けて保存」から直接Androidの保存画面を開き、名前と保存場所をまとめて指定する（事前のアプリ内名前入力ダイアログは廃止）。CreateEditorDocumentは候補名の拡張子からMIMEを選ぶ。Pythonはtext/x-python、JSはapplication/javascript、MDはtext/markdown等。未知の拡張子／拡張子なしでは独自のapplication/vnd.texteditor.documentを指定し、text/plainやワイルドカードへの補完を避ける。
- 保存先選択後は元ファイルのパスを渡さず、新しいURIへ書き込む。書き込みとストリームcloseが成功した後にだけ、URI／ファイル名／パス／元本文／未保存状態を更新する。キャンセル・失敗時は元の関連付けと未保存本文を維持する。
- 作成結果のDISPLAY_NAMEを確認し、候補名+.txtまたはコード拡張子／md等+.txtのような余分な拡張子が疑われる場合だけ、実際の名前付きの確認ダイアログを表示。通常の名前変更・連番名では表示しない。利用者が確認して確定した場合のみ、新規作成されたURIにDocumentsContract.renameDocumentを使用する。元ファイルや既存の.txtファイルは改名しない。改名後のURIと実名を再確認してから書き込む。提供元が改名不可／別名を強制する場合はエラーを表示し、未保存本文と元の関連付けを維持する。改名失敗・キャンセル時は空の作成済みファイルが残る可能性がある（自動削除しない）。
- pendingの候補名はAndroid保存状態に、保存後確認の名前・URIはDialogFragmentに保持。回転・プロセス再生成を経ても保存先照合を維持。書き込み後の実名をヘッダー・履歴・構文ハイライトに使用する。
- JVMテスト: 任意の拡張子・拡張子なし・日本語名、本文保持、書き込み後の名前取得、失敗時の関連付け維持。
- JS読込: アプリ内のOpenDocumentのフィルターがapplication/javascriptを含んでいなかった。application/javascript、application/x-javascript、application/ecmascript、application/typescriptと独自MIMEを追加。Manifestの外部VIEW/EDITにも同じ追加MIMEを宣言。text/*とapplication/octet-streamは維持。読み込みはUTF-8テキスト、コードは実行しない。
- 端末未確認: 新規→本文入力→名前を付けて保存でscript.py／README.md／日本語.txt／拡張子なしを指定→ファイル管理アプリとヘッダーで実名確認→再オープン。既存ファイルの別名保存で元ファイルが保持されること、キャンセル・書き込み失敗で元の関連付けと未保存本文が保持されること。Downloads、外部SD、クラウドの提供元で確認する。
- 追加端末確認: main.py→main.py.txtを返す提供元で名前確認→main.pyで保存→ファイル管理と再読込で確認。同名ファイル、意図したmain.py.txt、名前確認キャンセル、改名非対応、保存画面／名前確認で回転も確認。application/javascriptとして提供される.jsを「開く」と外部アプリから開き、編集・ハイライト・保存できること。
