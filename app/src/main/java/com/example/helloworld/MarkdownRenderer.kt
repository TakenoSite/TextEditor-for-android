package com.example.helloworld

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Document
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Paragraph
import org.commonmark.node.Node
import org.commonmark.node.HtmlBlock
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.OrderedList
import org.commonmark.renderer.html.HtmlRenderer

/** Pure JVM renderer: raw HTML is escaped and unsafe URL schemes are removed. */
internal object MarkdownRenderer {
    fun previewOnOpen(fileName: String): Boolean = fileName.endsWith(".md", ignoreCase = true)

    fun render(source: String, dark: Boolean): String {
        val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
        val parser = Parser.builder().extensions(extensions)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build()
        val prepared = preserveBoxes(source, parser)
        val document = parser.parse(prepared)
        preserveTrees(document, prepared, parser)
        val body = HtmlRenderer.builder().extensions(extensions)
            .escapeHtml(true).sanitizeUrls(true).build().render(document)
        val bg = if (dark) "#202124" else "#fafafa"
        val fg = if (dark) "#eceff1" else "#242424"
        val muted = if (dark) "#b0b5bc" else "#666666"
        val accent = if (dark) "#81c784" else "#25652a"
        val code = if (dark) "#292b2f" else "#eeeeee"
        return """<!doctype html><html lang="ja"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'; base-uri 'none'; form-action 'none'">
            <style>
            body{background:$bg;color:$fg;font:16px/1.7 sans-serif;margin:0;padding:20px;overflow-wrap:anywhere}
            h1,h2,h3,h4,h5,h6{line-height:1.35;margin:1.2em 0 .5em}h1{font-size:2em}h2{font-size:1.5em}
            a{color:$accent}pre,code{background:$code;font-family:monospace}code{padding:2px 4px;border-radius:4px}
            pre{padding:16px;overflow-x:auto;white-space:pre;overflow-wrap:normal}pre code{padding:0}
            blockquote{border-left:4px solid $muted;margin:16px 0;padding:0 16px;color:$muted}
            table{border-collapse:collapse;display:block;overflow-x:auto}th,td{border:1px solid $muted;padding:8px 12px}
            hr{border:0;border-top:1px solid $muted}img{display:none}li{margin:4px 0}
            </style></head><body>$body</body></html>""".trimIndent()
    }

    /** Fence whole box drawings before Markdown splits them into paragraphs and indented code. */
    private fun preserveBoxes(source: String, parser: Parser): String {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val protected = BooleanArray(lines.size)
        fun protect(node: Node) {
            val spans = node.sourceSpans
            if (spans.isNotEmpty()) {
                for (i in spans.first().lineIndex..spans.last().lineIndex) protected[i] = true
            }
        }
        parser.parse(source).accept(object : AbstractVisitor() {
            override fun visit(block: FencedCodeBlock) = protect(block)
            override fun visit(block: HtmlBlock) = protect(block)
            override fun visit(block: BlockQuote) = protect(block)
            override fun visit(block: BulletList) = protect(block)
            override fun visit(block: OrderedList) = protect(block)
        })
        val top = Regex("^ {0,3}[┌┏╔][─━═┬┳╦]+[┐┓╗][ \\t]*$")
        val bottom = Regex("^[ \\t]*[└┗╚][─━═┴┻╩]+[┘┛╝][ \\t]*$")
        val side = Regex("^[ \\t]*[│┃║].*[│┃║][ \\t]*$")
        val divider = Regex("^[ \\t]*[├┣╠][─━═┼╋╬┬┳╦┴┻╩]+[┤┫╣][ \\t]*$")
        val output = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            var end = index + 1
            if (!protected[index] && top.matches(lines[index])) {
                while (end < lines.size && !protected[end] && !bottom.matches(lines[end]) &&
                    (lines[end].isBlank() || side.matches(lines[end]) || divider.matches(lines[end]))) end++
                if (end < lines.size && !protected[end] && bottom.matches(lines[end])) {
                    val diagram = lines.subList(index, end + 1)
                    // Longer than any tilde run in the drawing, so its contents cannot close the fence.
                    val longest = diagram.maxOf { line -> Regex("~+").findAll(line).maxOfOrNull { it.value.length } ?: 0 }
                    val fence = "~".repeat(maxOf(3, longest + 1))
                    output.add("")
                    output.add(fence)
                    output.addAll(diagram)
                    output.add(fence)
                    output.add("")
                    index = end + 1
                    continue
                }
            }
            output.add(lines[index++])
        }
        return output.joinToString("\n")
    }

    /** A compatibility extension for unfenced tree diagrams, not general soft line breaks. */
    private fun preserveTrees(document: org.commonmark.node.Node, source: String, parser: Parser) {
        val sourceLines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val paragraphs = mutableListOf<Paragraph>()
        document.accept(object : AbstractVisitor() {
            override fun visit(paragraph: Paragraph) {
                // Do not reinterpret diagrams nested inside lists or block quotes.
                if (paragraph.parent is Document) paragraphs.add(paragraph)
            }
        })
        val branch = Regex("^[ \\t│┃]*[├└┣┗][─━]+.*$")
        val connector = Regex("^[ \\t│┃]*[│┃][ \\t│┃]*$")
        for (paragraph in paragraphs) {
            val spans = paragraph.sourceSpans
            if (spans.isEmpty()) continue
            val lines = sourceLines.subList(spans.first().lineIndex, spans.last().lineIndex + 1)
            val firstBranch = lines.indexOfFirst { branch.matches(it) }
            if (firstBranch < 0) continue
            val start = if (firstBranch > 0 && !connector.matches(lines[firstBranch - 1])) firstBranch - 1 else firstBranch
            var end = firstBranch + 1
            while (end < lines.size && (branch.matches(lines[end]) || connector.matches(lines[end]))) end++
            fun insertMarkdown(part: List<String>) {
                if (part.isEmpty()) return
                val parsed = parser.parse(part.joinToString("\n"))
                var child = parsed.firstChild
                while (child != null) {
                    val next = child.next
                    paragraph.insertBefore(child)
                    child = next
                }
            }
            insertMarkdown(lines.take(start))
            paragraph.insertBefore(FencedCodeBlock().apply {
                literal = lines.subList(start, end).joinToString("\n", postfix = "\n")
            })
            insertMarkdown(lines.drop(end))
            paragraph.unlink()
        }
    }
}
