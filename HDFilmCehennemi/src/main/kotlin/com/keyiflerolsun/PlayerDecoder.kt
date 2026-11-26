package com.keyiflerolsun

/**
 * closeload / rapidrame oynatÄ±cÄ± sayfasÄ±ndan GERÃ‡EK yayÄ±n adresini Ã§Ä±karÄ±r.
 *
 * CanlÄ± inceleme (Ekim 2026) sonucu:
 *  - Sayfada Ä°KÄ° karartÄ±lmÄ±ÅŸ adres Ã¼retilir. Biri tuzaktÄ±r (her filmde aynÄ±
 *    `.../filmakinesimp4-...mp4/master.txt`, JSON-LD `contentUrl` de bunu
 *    gÃ¶sterir); gerÃ§ek adres yalnÄ±zca `jwplayer().setup({sources:[{file:X}]})`
 *    iÃ§inde baÅŸvurulan deÄŸiÅŸkendir.
 *  - GerÃ§ek Ã§Ã¶zÃ¼cÃ¼nÃ¼n anahtarÄ± `location.hostname` ile karÄ±ÅŸtÄ±rÄ±lÄ±r; bu yÃ¼zden
 *    sayfanÄ±n SON (yÃ¶nlendirme sonrasÄ±) adresi verilmelidir.
 *  - Rapid oynatÄ±cÄ±sÄ±nda Ã§Ã¶zÃ¼cÃ¼ bir p,a,c,k,e,d paketinin iÃ§indedir ve sayfada
 *    yorum satÄ±rÄ±na alÄ±nmÄ±ÅŸ sahte bir `sources:[{file:atob(file_link)}]` vardÄ±r.
 *
 * YÃ¶ntem: satÄ±r iÃ§i script'ler (ve aÃ§Ä±lan paketler) MiniJs ile Ã§alÄ±ÅŸtÄ±rÄ±lÄ±r,
 * `sources` ifadesi deÄŸerlendirilir. Olmazsa tÃ¼m sayfa jwplayer taklidiyle
 * Ã§alÄ±ÅŸtÄ±rÄ±lÄ±p setup() yapÄ±landÄ±rmasÄ± yakalanÄ±r.
 */
internal object PlayerDecoder {

    data class Track(val file: String, val label: String, val kind: String, val language: String?)

    data class Decoded(val file: String, val tracks: List<Track>, val method: String)

    private val SCRIPT_RE = Regex(
        "<script\\b([^>]*)>(.*?)</script\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun inlineScripts(html: String): List<String> = SCRIPT_RE.findAll(html)
        .filter { m ->
            val attrs = m.groupValues[1].lowercase()
            !Regex("\\bsrc\\s*=").containsMatchIn(attrs) &&
                !attrs.contains("ld+json") && !attrs.contains("text/template") && !attrs.contains("text/html")
        }
        .map { it.groupValues[2] }
        .filter { it.isNotBlank() }
        .toList()

    private val NON_STREAM_EXT = listOf(
        ".vtt", ".srt", ".ass", ".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg", ".ico", ".css", ".js", ".html", ".htm", ".php"
    )

    fun isPlausibleStreamUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || url.length > 3000) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.any { it.isWhitespace() || it == '"' || it == '\'' || it == '<' || it == '>' || it == '\\' || it.code < 0x20 }) return false
        val uri = try { java.net.URI(url) } catch (e: Exception) { null } ?: return false
        val host = uri.host ?: return false
        if (!host.contains('.')) return false
        val path = (uri.rawPath ?: "").lowercase()
        // AltyazÄ±/gÃ¶rsel/script adresi yayÄ±n deÄŸildir.
        return NON_STREAM_EXT.none { path.endsWith(it) }
    }

    fun decode(html: String, pageUrl: String, targeted: Boolean = true): Decoded? {
        val scripts = inlineScripts(html)
        val chunks = ArrayList<String>()
        for (s in scripts) {
            chunks.add(s)
            try {
                chunks.addAll(Packer.unpackAll(s))
            } catch (e: Exception) {
                // paket aÃ§Ä±lamadÄ±
            }
        }

        val sourceExprs = ArrayList<Node>()
        val trackExprs = ArrayList<Node>()
        for (c in chunks) scanConfig(c, sourceExprs, trackExprs)

        // 1) Hedefli: sources ifadesinin baÄŸlÄ± olduÄŸu tanÄ±mlarÄ± Ã§alÄ±ÅŸtÄ±r.
        if (targeted && sourceExprs.isNotEmpty()) {
            val js = MiniJs(pageUrl, html)
            val executed = HashSet<Int>()
            for (expr in sourceExprs) {
                val names = freeIdents(expr)
                runDefinitions(js, chunks, names, executed)
                var attempt = 0
                while (attempt < 6) {
                    attempt++
                    val value = try {
                        js.evalNode(expr)
                    } catch (e: JsThrow) {
                        val missing = Regex("^(\\S+) is not defined").find(e.message)?.groupValues?.get(1)
                        if (missing != null && runDefinitions(js, chunks, setOf(missing), executed)) continue
                        null
                    } catch (e: Exception) {
                        null
                    }
                    val url = fileFromSources(js, value)
                    if (url != null) {
                        return Decoded(url, tracksFrom(js, trackExprs, pageUrl), "sources")
                    }
                    break
                }
            }
        }

        // 2) Genel: tÃ¼m script'leri sÄ±rayla Ã§alÄ±ÅŸtÄ±r, jwplayer setup() yakala.
        val js = MiniJs(pageUrl, html)
        for (s in scripts) {
            try {
                js.runCode(s)
            } catch (e: Exception) {
                // sonraki script
            } catch (e: StackOverflowError) {
                break
            }
        }
        for (cfg in js.capturedSetups.asReversed()) {
            val obj = cfg as? JsObject ?: continue
            val url = fileFromConfig(js, obj) ?: continue
            val tracks = (obj.props["tracks"] as? JsArray)?.let { tracksFromValue(js, it, pageUrl) }.orEmpty()
            return Decoded(url, tracks, "setup")
        }
        return null
    }

    /** `sources: [...]` ve `tracks: [...]` ifadelerini (yorum satÄ±rlarÄ± hariÃ§) bulur. */
    private fun scanConfig(code: String, sources: MutableList<Node>, tracks: MutableList<Node>) {
        if (!code.contains("sources") && !code.contains("tracks")) return
        val toks = try { Lexer(code).run() } catch (e: Exception) { return }
        for (i in 0 until toks.size - 2) {
            val t = toks[i]
            val key = when (t.type) {
                Tok.IDENT, Tok.STR -> t.text
                else -> continue
            }
            if (toks[i + 1].type != Tok.PUNCT || toks[i + 1].text != ":") continue
            // nesne anahtarÄ± olmalÄ±: Ã¶ncesi `{` veya `,`
            val prev = if (i > 0) toks[i - 1] else null
            if (prev == null || prev.type != Tok.PUNCT || (prev.text != "{" && prev.text != ",")) continue
            when (key) {
                "sources" -> parseValueAt(toks, i + 2)?.let { sources.add(it) }
                "tracks" -> parseValueAt(toks, i + 2)?.let { tracks.add(it) }
            }
        }
    }

    private fun parseValueAt(toks: List<Tok>, start: Int): Node? = try {
        Parser(toks, start).parseAssign()
    } catch (e: Exception) {
        null
    }

    private fun freeIdents(n: Node?, out: MutableSet<String> = LinkedHashSet()): Set<String> {
        when (n) {
            null -> {}
            is Ident -> out.add(n.name)
            is ArrayLit -> n.items.forEach { freeIdents(it, out) }
            is ObjectLit -> n.props.forEach { if (it.computed) freeIdents(it.key, out); freeIdents(it.value, out) }
            is MemberExpr -> { freeIdents(n.obj, out); if (n.computed) freeIdents(n.prop, out) }
            is CallExpr -> { freeIdents(n.callee, out); n.args.forEach { freeIdents(it, out) } }
            is NewExpr -> { freeIdents(n.callee, out); n.args.forEach { freeIdents(it, out) } }
            is BinaryExpr -> { freeIdents(n.l, out); freeIdents(n.r, out) }
            is LogicalExpr -> { freeIdents(n.l, out); freeIdents(n.r, out) }
            is CondExpr -> { freeIdents(n.test, out); freeIdents(n.cons, out); freeIdents(n.alt, out) }
            is UnaryExpr -> freeIdents(n.arg, out)
            is SeqExpr -> n.list.forEach { freeIdents(it, out) }
            is SpreadExpr -> freeIdents(n.arg, out)
            is TemplateLit -> n.exprs.forEach { freeIdents(it, out) }
            else -> {}
        }
        return out
    }

    /** `names` iÃ§inden birini tanÄ±mlayan (henÃ¼z Ã§alÄ±ÅŸtÄ±rÄ±lmamÄ±ÅŸ) parÃ§alarÄ± Ã§alÄ±ÅŸtÄ±rÄ±r. */
    private fun runDefinitions(js: MiniJs, chunks: List<String>, names: Set<String>, executed: MutableSet<Int>): Boolean {
        if (names.isEmpty()) return false
        var ran = false
        for ((idx, c) in chunks.withIndex()) {
            if (idx in executed) continue
            if (names.none { c.contains(it) }) continue
            if (!definesAny(c, names)) continue
            executed.add(idx)
            try {
                js.runCode(c)
                ran = true
            } catch (e: Exception) {
                // tolerans
            } catch (e: StackOverflowError) {
                // tolerans
            }
        }
        return ran
    }

    private fun definesAny(code: String, names: Set<String>): Boolean {
        val toks = try { Lexer(code).run() } catch (e: Exception) { return false }
        for (i in toks.indices) {
            val t = toks[i]
            if (t.type != Tok.IDENT || t.text !in names) continue
            val prev = if (i > 0) toks[i - 1] else null
            val next = if (i + 1 < toks.size) toks[i + 1] else null
            if (prev != null && prev.type == Tok.IDENT && (prev.text == "var" || prev.text == "let" || prev.text == "const" || prev.text == "function")) return true
            if (next != null && next.type == Tok.PUNCT && next.text == "=" && (prev == null || prev.type != Tok.PUNCT || prev.text != ".")) return true
            if (prev != null && prev.type == Tok.PUNCT && prev.text == "," && next != null && next.type == Tok.PUNCT && next.text == "=") return true
        }
        return false
    }

    private fun fileFromSources(js: MiniJs, value: Any?): String? {
        when (value) {
            is String -> return value.trim().takeIf { isPlausibleStreamUrl(it) }
            is JsArray -> {
                for (item in value.list) {
                    val f = when (item) {
                        is String -> item
                        is JsObject -> item.props["file"] ?: item.props["src"] ?: item.props["url"]
                        else -> null
                    }
                    if (f is String && isPlausibleStreamUrl(f.trim())) return f.trim()
                }
            }
            is JsObject -> {
                val f = value.props["file"] ?: value.props["src"]
                if (f is String && isPlausibleStreamUrl(f.trim())) return f.trim()
            }
        }
        return null
    }

    private fun fileFromConfig(js: MiniJs, cfg: JsObject): String? {
        fileFromSources(js, cfg.props["sources"])?.let { return it }
        (cfg.props["file"] as? String)?.trim()?.takeIf { isPlausibleStreamUrl(it) }?.let { return it }
        val playlist = cfg.props["playlist"]
        if (playlist is JsArray) {
            for (item in playlist.list) {
                if (item is JsObject) {
                    fileFromSources(js, item.props["sources"])?.let { return it }
                    (item.props["file"] as? String)?.trim()?.takeIf { isPlausibleStreamUrl(it) }?.let { return it }
                }
            }
        }
        return null
    }

    private fun tracksFrom(js: MiniJs, exprs: List<Node>, pageUrl: String): List<Track> {
        val out = ArrayList<Track>()
        for (e in exprs) {
            val v = try { js.evalNode(e) } catch (ex: Exception) { null } ?: continue
            if (v is JsArray) out.addAll(tracksFromValue(js, v, pageUrl))
        }
        return out.distinctBy { it.file }
    }

    private fun tracksFromValue(js: MiniJs, arr: JsArray, pageUrl: String): List<Track> {
        val out = ArrayList<Track>()
        for (item in arr.list) {
            val o = item as? JsObject ?: continue
            val file = (o.props["file"] ?: o.props["src"]) as? String ?: continue
            val kind = (o.props["kind"] as? String)?.lowercase() ?: "captions"
            if (kind != "captions" && kind != "subtitles") continue
            val abs = absolutize(file.trim(), pageUrl) ?: continue
            val label = (o.props["label"] as? String)?.trim().orEmpty()
            val lang = (o.props["language"] ?: o.props["srclang"]) as? String
            out.add(Track(abs, label, kind, lang))
        }
        return out
    }

    fun absolutize(raw: String, base: String): String? {
        if (raw.isBlank()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (raw.startsWith("//")) return "https:$raw"
        return try {
            java.net.URI(base).resolve(raw.replace(" ", "%20")).toString()
        } catch (e: Exception) {
            null
        }
    }
}

