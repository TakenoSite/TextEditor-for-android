package com.example.helloworld

import java.util.Locale

/** Offline, bounded lexical highlighting. Never executes or modifies source code. */
internal object SyntaxLexer {
    enum class Language { C, CPP, PYTHON, JAVASCRIPT, TYPESCRIPT, JAVA, KOTLIN, CSHARP, GO, RUST, SWIFT }
    enum class Kind { KEYWORD, STRING, COMMENT, NUMBER, TYPE, DIRECTIVE }
    data class Token(val start: Int, val end: Int, val kind: Kind)
    const val MAX_LENGTH = 500_000
    const val MAX_TOKENS = 20_000

    fun language(fileName: String): Language? = when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "c", "h" -> Language.C
        "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> Language.CPP
        "py", "pyw", "pyi" -> Language.PYTHON
        "js", "jsx", "mjs", "cjs" -> Language.JAVASCRIPT
        "ts", "tsx", "mts", "cts" -> Language.TYPESCRIPT
        "java" -> Language.JAVA
        "kt", "kts" -> Language.KOTLIN
        "cs" -> Language.CSHARP
        "go" -> Language.GO
        "rs" -> Language.RUST
        "swift" -> Language.SWIFT
        else -> null
    }

    private fun words(value: String) = value.split(' ').toSet()
    private val common = words("if else for while do break continue return switch case default class struct enum public private protected static const void try catch finally throw new true false null import package interface extends implements this super in as is async await")
    private val keywords = mapOf(
        Language.C to words("auto register extern signed unsigned sizeof typedef union volatile restrict inline _Bool _Atomic _Generic _Thread_local"),
        Language.CPP to words("alignas alignof auto bool constexpr consteval constinit decltype delete dynamic_cast explicit export extern friend mutable namespace noexcept nullptr operator override final reinterpret_cast requires static_assert static_cast template thread_local typedef typename union using virtual volatile co_await co_return co_yield concept"),
        Language.PYTHON to words("False None True and as assert async await def del elif except from global import in is lambda nonlocal not or pass raise with yield match case"),
        Language.JAVASCRIPT to words("function let var const export from of typeof instanceof delete undefined yield debugger get set"),
        Language.TYPESCRIPT to words("function let var const export from of typeof instanceof delete undefined yield debugger get set type declare namespace readonly keyof infer never unknown any abstract satisfies implements"),
        Language.JAVA to words("abstract assert boolean byte char double final float int long native short strictfp synchronized throws transient volatile record sealed permits non-sealed var instanceof module requires exports"),
        Language.KOTLIN to words("fun val var object companion data sealed override open internal lateinit by when where constructor init typealias suspend inline crossinline noinline reified expect actual out operator infix tailrec external annotation dynamic field property get set"),
        Language.CSHARP to words("abstract base bool byte checked decimal delegate event explicit fixed implicit int lock long nameof operator out override params readonly ref sbyte sealed short stackalloc string uint ulong unchecked unsafe ushort using virtual volatile partial record init required with yield namespace var dynamic get set value"),
        Language.GO to words("func var type map chan defer go range select fallthrough nil make len cap append copy close complex real imag recover panic iota"),
        Language.RUST to words("fn let mut pub impl trait use mod crate self Self match loop move ref dyn where unsafe extern type union macro_rules Some None Ok Err"),
        Language.SWIFT to words("func let var protocol extension guard defer repeat fallthrough nil self Self associatedtype typealias fileprivate internal open final override lazy weak unowned mutating nonmutating throws rethrows init deinit subscript some any actor nonisolated required convenience")
    )
    private val builtins = words("int long short char float double bool boolean byte size_t string String Int Int8 Int16 Int32 Int64 UInt UInt8 UInt16 UInt32 UInt64 Float Double Bool Character Any Unit Nothing Byte Short Long Char Boolean Array List Map Set Object Number Promise number bigint symbol unknown i8 i16 i32 i64 i128 u8 u16 u32 u64 u128 usize isize f32 f64 str int8 int16 int32 int64 uint uint8 uint16 uint32 uint64 float32 float64 rune error complex64 complex128 list dict tuple set bytes str")

    fun tokenize(source: String, language: Language?): List<Token> {
        if (language == null || source.length > MAX_LENGTH) return emptyList()
        val result = ArrayList<Token>()
        val python = language == Language.PYTHON
        val nestedComments = language in setOf(Language.KOTLIN, Language.RUST, Language.SWIFT)
        var i = 0
        fun starts(value: String, at: Int = i) = source.startsWith(value, at)
        fun emit(start: Int, kind: Kind) { result.add(Token(start, i, kind)) }
        fun lineEnd() { while (i < source.length && source[i] != '\n' && source[i] != '\r') i++ }
        fun quoted(delimiter: String, multiline: Boolean, doubled: Boolean = false, raw: Boolean = false) {
            i += delimiter.length
            while (i < source.length) {
                if (starts(delimiter)) {
                    i += delimiter.length
                    if (doubled && starts(delimiter)) { i += delimiter.length; continue }
                    break
                }
                if (!multiline && (source[i] == '\n' || source[i] == '\r')) break
                if (!doubled && !raw && source[i] == '\\' && i + 1 < source.length) i += 2 else i++
            }
        }
        while (i < source.length && result.size < MAX_TOKENS) {
            if (Thread.currentThread().isInterrupted) return emptyList()
            val start = i
            val c = source[i]
            when {
                (python && c == '#') || (!python && starts("//")) -> {
                    lineEnd(); emit(start, Kind.COMMENT)
                }
                !python && starts("/*") -> {
                    i += 2
                    var depth = 1
                    while (i < source.length && depth > 0) {
                        when {
                            starts("*/") -> { depth--; i += 2 }
                            nestedComments && starts("/*") -> { depth++; i += 2 }
                            else -> i++
                        }
                    }
                    emit(start, Kind.COMMENT)
                }
                (language == Language.C || language == Language.CPP || language == Language.CSHARP) && c == '#' -> {
                    i++
                    while (i < source.length && (source[i] == ' ' || source[i] == '\t')) i++
                    while (i < source.length && source[i].isLetter()) i++
                    emit(start, Kind.DIRECTIVE)
                }
                language == Language.RUST && c == 'r' && i + 1 < source.length && source[i + 1] in "#\"" -> {
                    var quote = i + 1
                    while (quote < source.length && source[quote] == '#') quote++
                    if (quote < source.length && source[quote] == '"') {
                        val closing = "\"" + "#".repeat(quote - start - 1)
                        val end = source.indexOf(closing, quote + 1)
                        i = if (end < 0) source.length else end + closing.length
                        emit(start, Kind.STRING)
                    } else i++
                }
                language == Language.CPP && starts("R\"") -> {
                    val open = source.indexOf('(', i + 2)
                    if (open in (i + 2)..minOf(i + 18, source.lastIndex)) {
                        val closing = ")" + source.substring(i + 2, open) + "\""
                        val end = source.indexOf(closing, open + 1)
                        i = if (end < 0) source.length else end + closing.length
                        emit(start, Kind.STRING)
                    } else i++
                }
                language == Language.CSHARP && starts("@\"") -> {
                    i++; quoted("\"", multiline = true, doubled = true); emit(start, Kind.STRING)
                }
                (python || language == Language.KOTLIN || language == Language.JAVA || language == Language.SWIFT || language == Language.CSHARP) && starts("\"\"\"") -> {
                    quoted("\"\"\"", true, raw = language == Language.KOTLIN); emit(start, Kind.STRING)
                }
                python && starts("'''" ) -> { quoted("'''", true); emit(start, Kind.STRING) }
                language == Language.RUST && c == '\'' && i + 1 < source.length && source[i + 1].isLetter() &&
                    (i + 2 >= source.length || source[i + 2] != '\'') -> {
                    i += 2
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    emit(start, Kind.TYPE)
                }
                c == '"' || c == '\'' || (c == '`' && language in setOf(Language.GO, Language.JAVASCRIPT, Language.TYPESCRIPT)) -> {
                    quoted(c.toString(), c == '`', raw = c == '`' && language == Language.GO); emit(start, Kind.STRING)
                }
                c.isDigit() -> {
                    i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] in "._" ||
                        (source[i] in "+-" && source[i - 1] in "eEpP"))) i++
                    emit(start, Kind.NUMBER)
                }
                c == '@' && !python -> {
                    i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] in "_.")) i++
                    emit(start, Kind.DIRECTIVE)
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] in "_$")) i++
                    val word = source.substring(start, i)
                    when {
                        word in keywords.getValue(language) || word in common -> emit(start, Kind.KEYWORD)
                        word in builtins -> emit(start, Kind.TYPE)
                    }
                }
                else -> i++
            }
        }
        return result
    }
}
