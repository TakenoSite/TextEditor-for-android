package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class MarkdownRendererTest {
    @Test fun rendersReportedCrashDocumentInBothThemes() {
        val source = requireNotNull(javaClass.getResource("/markdown/orchestrator-design-improvements.md")).readText()
        listOf(false, true).forEach { dark ->
            repeat(3) {
                val html = MarkdownRenderer.render(source, dark)
                assertTrue(html.contains("<h1>L1向けオーケストレーター設計改善策</h1>"))
                assertEquals(4, Regex("<table>").findAll(html).count())
                assertTrue(html.contains("<pre><code class=\"language-text\">L1 利用者窓口"))
                assertTrue(html.contains("<h2>12. 導入順</h2>"))
                assertFalse(html.contains("<script"))
            }
        }
    }

    @Test fun preservesHudBoxAcrossBlankLinesAndIndentedBottom() {
        val diagram = "┌────────────────────────────────────┐\n│ OUTPOST ⚙       TRAINING AREA       │\n│                                    │\n│ [MOVE] [FIRE]     [RELOAD] [JUMP]    │\n\n    │              30 / 30          │\n    └────────────────────────────────────┘"
        val html = MarkdownRenderer.render("## HUDレイアウト\n\n推奨構成：\n\n$diagram\n\n画面中央は3D空間の表示を優先する。", true)
        assertTrue("Entire box must be a single preformatted block", html.contains("<pre><code>$diagram\n</code></pre>"))
        assertEquals(1, Regex("<pre>").findAll(html).count())
        assertTrue(html.contains("<p>画面中央は3D空間の表示を優先する。</p>"))
    }

    @Test fun keepsExistingCodeAndMarkdownExamplesUnchanged() {
        val source = "```markdown\n┌───┐\n│ x │\n└───┘\n```\n\n> ┌───┐\n> │ x │\n> └───┘\n\n- ┌───┐\n  │ x │\n  └───┘"
        val html = MarkdownRenderer.render(source, false)
        assertTrue(html.contains("<pre><code class=\"language-markdown\">┌───┐\n│ x │\n└───┘\n</code></pre>"))
        assertEquals(1, Regex("<pre>").findAll(html).count())
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("<ul>"))
    }

    @Test fun supportsDoubleBorderAndEscapesBoxContents() {
        val html = MarkdownRenderer.render("╔══════╗\r\n║ <b>  ║\r\n╠══════╣\r\n║ ~~~  ║\r\n╚══════╝", false)
        assertTrue(html.contains("<pre><code>╔══════╗\n║ &lt;b&gt;  ║\n╠══════╣\n║ ~~~  ║\n╚══════╝\n</code></pre>"))
        assertFalse(html.contains("<b>"))
    }

    @Test fun doesNotConsumeProseAfterIncompleteBox() {
        val html = MarkdownRenderer.render("┌────┐\n│ UI │\n\n通常の**文章**\n\n## 次の節", false)
        assertFalse(html.contains("<pre>"))
        assertTrue(html.contains("通常の<strong>文章</strong>"))
        assertTrue(html.contains("<h2>次の節</h2>"))
    }

    @Test fun keepsMultipleBoxesSeparateFromProse() {
        val box = "┌───┐\n│ x │\n└───┘"
        val html = MarkdownRenderer.render("説明\n$box\n間の文章\n$box\n終わり", false)
        assertEquals(2, Regex("<pre>").findAll(html).count())
        assertTrue(html.contains("<p>間の文章</p>"))
        assertTrue(html.contains("<p>終わり</p>"))
    }

    @Test fun preservesUnfencedAudioTree() {
        val tree = "assets/\n└── audio/\n    ├── ambient/\n    │   ├── wind.ogg\n    │   └── birds_01.ogg\n    │\n    └── ui/\n        ├── click.wav\n        └── confirm.wav"
        val html = MarkdownRenderer.render("音声ファイルは以下の構造で管理する。\n\n$tree\n\n音源はライセンスを確認したものを使用する。", true)
        assertTrue(html.contains("<pre><code>$tree\n</code></pre>"))
        assertTrue(html.contains("<p>音源はライセンスを確認したものを使用する。</p>"))
    }

    @Test fun separatesTreeFromAdjacentProse() {
        val html = MarkdownRenderer.render("既存Node構造：\nM4_Root\n├── Body\n├── MovingParts\n│   └── Trigger\n└── AttachmentPoints\n各Nodeの**独立**したTransformを維持する。", false)
        assertTrue(html.contains("<p>既存Node構造：</p>"))
        assertTrue(html.contains("<pre><code>M4_Root\n├── Body"))
        assertTrue(html.contains("<p>各Nodeの<strong>独立</strong>したTransformを維持する。</p>"))
    }

    @Test fun leavesFencedTreeAndNormalParagraphsAlone() {
        val html = MarkdownRenderer.render("```text\nroot\n└── child\n```\n\n通常の文章\n次の行\n\n記号の説明：├── は枝です。", false)
        assertTrue(html.contains("<pre><code class=\"language-text\">root\n└── child\n</code></pre>"))
        assertTrue(html.contains("<p>通常の文章\n次の行</p>"))
        assertTrue(html.contains("<p>記号の説明：├── は枝です。</p>"))
    }

    @Test fun escapesHtmlInUnfencedTreeAndSupportsCrLf() {
        val html = MarkdownRenderer.render("root/\r\n└── <script>alert(1)</script>", false)
        assertTrue(html.contains("<pre><code>root/\n└── &lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script>"))
    }

    @Test fun opensMdInPreviewIgnoringCase() {
        listOf("README.md", "README.MD", "note.Md", "日本語.md").forEach {
            assertTrue(it, MarkdownRenderer.previewOnOpen(it))
        }
    }

    @Test fun opensOtherExtensionsInEditor() {
        listOf("無題.txt", "README", "notes.md.txt", "notes.markdown", "").forEach {
            assertFalse(it, MarkdownRenderer.previewOnOpen(it))
        }
    }

    @Test fun rendersReadmeBlocks() {
        val html = MarkdownRenderer.render("# 日本語README\n\n- one\n- two\n\n> quote\n\n```kotlin\nval x = 1\n```", false)
        assertTrue(html.contains("<h1>日本語README</h1>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("<pre><code class=\"language-kotlin\">val x = 1"))
    }

    @Test fun rendersInlineAndTable() {
        val html = MarkdownRenderer.render("**bold** *italic* `code` ~~old~~ [site](https://example.com)\n\n| A | B |\n| --- | --- |\n| 1 | 2 |", false)
        listOf("<strong>bold</strong>", "<em>italic</em>", "<code>code</code>", "<del>old</del>", "<table>", "href=\"https://example.com\"").forEach {
            assertTrue(it, html.contains(it))
        }
    }

    @Test fun escapesRawHtmlAndCode() {
        val html = MarkdownRenderer.render("<script>alert(1)</script>\n\n```\n<img src=x onerror=alert(1)>\n```", false)
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("<img src=x"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&lt;img"))
    }

    @Test fun sanitizesExecutableUrls() {
        val html = MarkdownRenderer.render("[bad](javascript:alert%281%29)\n\n![bad](file:///secret)", false)
        assertFalse(html.contains("href=\"javascript:"))
        assertFalse(html.contains("src=\"file:"))
        assertTrue(html.contains("default-src 'none'"))
        assertTrue(html.contains("img-src 'none'"))
    }

    @Test fun supportsEmptyAndUnfinishedMarkdown() {
        assertTrue(MarkdownRenderer.render("", false).contains("<body></body>"))
        val html = MarkdownRenderer.render("```\n**unfinished", true)
        assertTrue(html.contains("<pre><code>**unfinished"))
        assertTrue(html.contains("background:#202124"))
    }

    @Test fun leavesSourceUnchanged() {
        val source = "# 未保存\n\nhello  \nworld\n"
        val copy = source.toCharArray().concatToString()
        MarkdownRenderer.render(source, false)
        assertEquals(copy, source)
    }
}
