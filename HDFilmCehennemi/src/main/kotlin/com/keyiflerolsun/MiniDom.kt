package com.keyiflerolsun

/**
 * Oynatıcı sayfasındaki gerçek yayın adresi DOM'a bağlı bir korumayla üretiliyor:
 *
 * ```js
 * sources: [{file:(function(){
 *   var e = document.querySelector('[data-v]');
 *   if (!e || e.getAttribute('data-v') !== "F2Q4NVU8") return '';
 *   return ub9k([...]);
 * })()}]
 * ```
 *
 * `data-v` değeri her sayfada rastgele olduğu için sabit yazılamaz; MiniJs'e
 * sayfanın küçük bir DOM taklidi verilir ve değer HTML'den okunur.
 */
internal object MiniDom {

    private val SCRIPT_BLOCK = Regex("(?is)<script\\b[^>]*>.*?</script\\s*>")
    private val STYLE_BLOCK = Regex("(?is)<style\\b[^>]*>.*?</style\\s*>")
    private val COMMENT_BLOCK = Regex("(?s)<!--.*?-->")
    private val TAG_RE = Regex("<([a-zA-Z][\\w:-]*)((?:\\s[^<>]*?)?)/?>")
    private val ATTR_RE = Regex("([\\w:.-]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?")

    class Element(val tag: String, val attrs: Map<String, String>) {
        val js: JsObject = JsObject().also { o ->
            o.props["tagName"] = tag.uppercase()
            o.props["nodeName"] = tag.uppercase()
            o.props["id"] = attrs["id"] ?: ""
            o.props["className"] = attrs["class"] ?: ""
            o.props["textContent"] = ""
            o.props["innerText"] = ""
            o.props["innerHTML"] = ""
            o.props["style"] = JsObject().also { s ->
                s.props["getPropertyValue"] = NativeFunction("getPropertyValue") { _, _ -> "" }
            }
            o.props["classList"] = JsObject().also { c ->
                val classes = (attrs["class"] ?: "").split(' ').filter { it.isNotBlank() }
                c.props["contains"] = NativeFunction("contains") { _, a -> classes.contains(str(a.getOrNull(0))) }
                c.props["add"] = NativeFunction("add") { _, _ -> Undefined }
                c.props["remove"] = NativeFunction("remove") { _, _ -> Undefined }
            }
            o.props["getAttribute"] = NativeFunction("getAttribute") { _, a ->
                attrs[str(a.getOrNull(0)).lowercase()]
            }
            o.props["hasAttribute"] = NativeFunction("hasAttribute") { _, a ->
                attrs.containsKey(str(a.getOrNull(0)).lowercase())
            }
            o.props["removeAttribute"] = NativeFunction("removeAttribute") { _, _ -> Undefined }
            o.props["setAttribute"] = NativeFunction("setAttribute") { _, _ -> Undefined }
            o.props["getAttributeNames"] = NativeFunction("getAttributeNames") { _, _ ->
                JsArray(attrs.keys.toMutableList())
            }
            o.props["querySelector"] = NativeFunction("querySelector") { _, _ -> null }
            o.props["querySelectorAll"] = NativeFunction("querySelectorAll") { _, _ -> JsArray() }
        }
    }

    private fun str(v: Any?): String = when {
        v == null || v === Undefined -> ""
        v is String -> v
        else -> v.toString()
    }

    private fun parse(html: String): List<Element> {
        if (html.isBlank()) return emptyList()
        val cleaned = html
            .replace(COMMENT_BLOCK, " ")
            .replace(SCRIPT_BLOCK, " ")
            .replace(STYLE_BLOCK, " ")
        val out = ArrayList<Element>()
        for (m in TAG_RE.findAll(cleaned)) {
            val attrs = LinkedHashMap<String, String>()
            for (a in ATTR_RE.findAll(m.groupValues[2])) {
                val name = a.groupValues[1].lowercase()
                if (name.isEmpty()) continue
                val value = when {
                    a.groupValues[2].isNotEmpty() -> a.groupValues[2]
                    a.groupValues[3].isNotEmpty() -> a.groupValues[3]
                    a.groupValues[4].isNotEmpty() -> a.groupValues[4]
                    else -> ""
                }
                attrs[name] = value
            }
            out.add(Element(m.groupValues[1].lowercase(), attrs))
        }
        return out
    }

    /** `document` nesnesine querySelector/getElementById gibi üyeleri ekler. */
    fun install(document: JsObject, html: String) {
        val elements = parse(html)
        document.props["querySelector"] = NativeFunction("querySelector") { _, a ->
            val sel = str(a.getOrNull(0))
            select(elements, sel)?.js
        }
        document.props["querySelectorAll"] = NativeFunction("querySelectorAll") { _, a ->
            val sel = str(a.getOrNull(0))
            JsArray(selectAll(elements, sel).map { it.js }.toMutableList())
        }
        document.props["getElementById"] = NativeFunction("getElementById") { _, a ->
            val id = str(a.getOrNull(0))
            elements.firstOrNull { it.attrs["id"] == id }?.js
        }
        document.props["getElementsByClassName"] = NativeFunction("getElementsByClassName") { _, a ->
            val cls = str(a.getOrNull(0))
            JsArray(
                elements.filter { e -> (e.attrs["class"] ?: "").split(' ').contains(cls) }
                    .map { it.js }.toMutableList()
            )
        }
        document.props["getElementsByTagName"] = NativeFunction("getElementsByTagName") { _, a ->
            val tag = str(a.getOrNull(0)).lowercase()
            JsArray(elements.filter { it.tag == tag }.map { it.js }.toMutableList())
        }
        document.props["createElement"] = NativeFunction("createElement") { _, a ->
            Element(str(a.getOrNull(0)).ifBlank { "div" }, emptyMap()).js
        }
        document.props["documentElement"] =
            (elements.firstOrNull { it.tag == "html" } ?: Element("html", emptyMap())).js
        document.props["body"] =
            (elements.firstOrNull { it.tag == "body" } ?: Element("body", emptyMap())).js
        document.props["head"] =
            (elements.firstOrNull { it.tag == "head" } ?: Element("head", emptyMap())).js
    }

    private fun select(elements: List<Element>, selector: String): Element? =
        selectAll(elements, selector).firstOrNull()

    private fun selectAll(elements: List<Element>, selector: String): List<Element> {
        val parts = selector.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        return elements.filter { el -> parts.any { matches(el, it) } }
    }

    /** Basit seçici desteği: `tag`, `#id`, `.sinif`, `[attr]`, `[attr="deger"]`. */
    private fun matches(el: Element, selector: String): Boolean {
        var s = selector.trim()
        if (s.isEmpty()) return false
        // soy seçiciler: son parçayı kullan
        if (s.contains(' ') && !s.contains('[')) s = s.substringAfterLast(' ')
        // pseudo sınıfları yok say
        val colon = s.indexOf(':')
        if (colon > 0) s = s.substring(0, colon)

        var i = 0
        val tagMatch = Regex("^[a-zA-Z][\\w-]*").find(s)
        if (tagMatch != null && tagMatch.range.first == 0) {
            if (!el.tag.equals(tagMatch.value, ignoreCase = true)) return false
            i = tagMatch.value.length
        }
        while (i < s.length) {
            when (s[i]) {
                '#' -> {
                    val m = Regex("^#([\\w-]+)").find(s.substring(i)) ?: return false
                    if (el.attrs["id"] != m.groupValues[1]) return false
                    i += m.value.length
                }
                '.' -> {
                    val m = Regex("^\\.([\\w-]+)").find(s.substring(i)) ?: return false
                    val cls = m.groupValues[1]
                    if (!(el.attrs["class"] ?: "").split(' ').contains(cls)) return false
                    i += m.value.length
                }
                '[' -> {
                    val end = s.indexOf(']', i)
                    if (end < 0) return false
                    val body = s.substring(i + 1, end)
                    val eq = body.indexOf('=')
                    if (eq < 0) {
                        if (!el.attrs.containsKey(body.trim().lowercase())) return false
                    } else {
                        val name = body.substring(0, eq).trim().lowercase()
                        val value = body.substring(eq + 1).trim().trim('"', '\'')
                        if (el.attrs[name] != value) return false
                    }
                    i = end + 1
                }
                else -> i++
            }
        }
        return true
    }
}
