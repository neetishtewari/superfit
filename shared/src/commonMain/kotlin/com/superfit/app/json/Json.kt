package com.superfit.app.json

/*
 * A small JSON library that works on Android and iPhone alike. It copies the parts of Android's
 * org.json that Superfit uses, with the same forgiving rules, so the AI's answers and the app's
 * saved data read exactly as before:
 * - text after the first complete value is ignored, as are comments;
 * - strings may use single quotes, and bare words are read as strings;
 * - numbers given as text still read as numbers (optDouble("5") is 5.0), and the reverse;
 * - optString on a JSON null gives "null", like Android.
 */

class JSONException(message: String) : RuntimeException(message)

class JSONObject() {
    private val values = LinkedHashMap<String, Any?>()

    constructor(json: String) : this() {
        val value = JSONTokener(json).nextValue()
        if (value !is JSONObject) throw typeMismatch(value, "JSONObject")
        values.putAll(value.values)
    }

    constructor(map: Map<*, *>) : this() {
        for ((k, v) in map) values[k?.toString() ?: throw JSONException("Names must be non-null")] = wrap(v)
    }

    fun length(): Int = values.size

    fun has(name: String): Boolean = values.containsKey(name)

    fun isNull(name: String): Boolean {
        val value = values[name]
        return value == null || value === NULL
    }

    fun keys(): Iterator<String> = values.keys.toList().iterator()

    fun opt(name: String): Any? = values[name]

    fun remove(name: String): Any? = values.remove(name)

    /** Stores a value; a null value removes the name, as on Android. */
    fun put(name: String, value: Any?): JSONObject {
        if (value == null) {
            values.remove(name)
            return this
        }
        if (value is Number) checkDouble(value.toDouble())
        values[name] = value
        return this
    }

    fun get(name: String): Any = values[name] ?: throw JSONException("No value for $name")

    fun getString(name: String): String = toStringValue(get(name)) ?: throw typeMismatch(name, get(name), "String")

    fun getDouble(name: String): Double = toDoubleValue(get(name)) ?: throw typeMismatch(name, get(name), "double")

    fun getInt(name: String): Int = toIntValue(get(name)) ?: throw typeMismatch(name, get(name), "int")

    fun getLong(name: String): Long = toLongValue(get(name)) ?: throw typeMismatch(name, get(name), "long")

    fun getBoolean(name: String): Boolean = toBooleanValue(get(name)) ?: throw typeMismatch(name, get(name), "boolean")

    fun getJSONObject(name: String): JSONObject = get(name) as? JSONObject ?: throw typeMismatch(name, get(name), "JSONObject")

    fun getJSONArray(name: String): JSONArray = get(name) as? JSONArray ?: throw typeMismatch(name, get(name), "JSONArray")

    fun optString(name: String, fallback: String = ""): String = toStringValue(opt(name)) ?: fallback

    fun optDouble(name: String, fallback: Double = Double.NaN): Double = toDoubleValue(opt(name)) ?: fallback

    fun optInt(name: String, fallback: Int = 0): Int = toIntValue(opt(name)) ?: fallback

    fun optLong(name: String, fallback: Long = 0L): Long = toLongValue(opt(name)) ?: fallback

    fun optBoolean(name: String, fallback: Boolean = false): Boolean = toBooleanValue(opt(name)) ?: fallback

    fun optJSONObject(name: String): JSONObject? = opt(name) as? JSONObject

    fun optJSONArray(name: String): JSONArray? = opt(name) as? JSONArray

    override fun toString(): String = StringBuilder().also { write(it) }.toString()

    internal fun write(out: StringBuilder) {
        out.append('{')
        var first = true
        for ((k, v) in values) {
            if (!first) out.append(',')
            first = false
            writeString(out, k)
            out.append(':')
            writeValue(out, v)
        }
        out.append('}')
    }

    companion object {
        /** JSON's null, kept so a saved null stays distinct from a missing name. */
        val NULL: Any = object {
            override fun equals(other: Any?): Boolean = other == null || other === this
            override fun hashCode(): Int = 0
            override fun toString(): String = "null"
        }

        /** Turns a plain value into one JSON can hold: lists become arrays and maps objects. */
        fun wrap(value: Any?): Any? = when (value) {
            null -> NULL
            is JSONObject, is JSONArray, is String, is Number, is Boolean -> value
            NULL -> value
            is Collection<*> -> JSONArray(value)
            is Array<*> -> JSONArray(value.toList())
            is Map<*, *> -> JSONObject(value)
            else -> value.toString()
        }
    }
}

class JSONArray() {
    private val values = ArrayList<Any?>()

    constructor(json: String) : this() {
        val value = JSONTokener(json).nextValue()
        if (value !is JSONArray) throw typeMismatch(value, "JSONArray")
        values.addAll(value.values)
    }

    constructor(items: Collection<*>) : this() {
        for (item in items) values.add(JSONObject.wrap(item))
    }

    fun length(): Int = values.size

    fun put(value: Any?): JSONArray {
        values.add(value)
        return this
    }

    fun opt(index: Int): Any? = values.getOrNull(index)

    fun isNull(index: Int): Boolean {
        val value = opt(index)
        return value == null || value === JSONObject.NULL
    }

    fun get(index: Int): Any = opt(index) ?: throw JSONException("Value at $index is null.")

    fun getString(index: Int): String = toStringValue(get(index)) ?: throw typeMismatch(index, get(index), "String")

    fun getDouble(index: Int): Double = toDoubleValue(get(index)) ?: throw typeMismatch(index, get(index), "double")

    fun getInt(index: Int): Int = toIntValue(get(index)) ?: throw typeMismatch(index, get(index), "int")

    fun getJSONObject(index: Int): JSONObject = get(index) as? JSONObject ?: throw typeMismatch(index, get(index), "JSONObject")

    fun getJSONArray(index: Int): JSONArray = get(index) as? JSONArray ?: throw typeMismatch(index, get(index), "JSONArray")

    fun optString(index: Int, fallback: String = ""): String = toStringValue(opt(index)) ?: fallback

    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = toDoubleValue(opt(index)) ?: fallback

    fun optInt(index: Int, fallback: Int = 0): Int = toIntValue(opt(index)) ?: fallback

    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = toBooleanValue(opt(index)) ?: fallback

    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    override fun toString(): String = StringBuilder().also { write(it) }.toString()

    internal fun write(out: StringBuilder) {
        out.append('[')
        values.forEachIndexed { i, v ->
            if (i > 0) out.append(',')
            writeValue(out, v)
        }
        out.append(']')
    }
}

/** Reads JSON text with the same forgiving rules as Android's org.json. */
class JSONTokener(private val input: String) {
    private var pos = 0

    fun nextValue(): Any? {
        return when (val c = nextClean()) {
            -1 -> throw JSONException("End of input at character $pos")
            '{'.code -> readObject()
            '['.code -> readArray()
            '\''.code, '"'.code -> nextString(c.toChar())
            else -> {
                pos--
                readLiteral()
            }
        }
    }

    private fun nextClean(): Int {
        while (pos < input.length) {
            val c = input[pos++]
            when (c) {
                '\t', ' ', '\n', '\r' -> continue
                '/' -> {
                    if (pos == input.length) return c.code
                    when (input[pos]) {
                        '*' -> {
                            pos++
                            val end = input.indexOf("*/", pos)
                            if (end == -1) throw JSONException("Unterminated comment")
                            pos = end + 2
                        }
                        '/' -> {
                            pos++
                            skipToEndOfLine()
                        }
                        else -> return c.code
                    }
                }
                '#' -> skipToEndOfLine()
                else -> return c.code
            }
        }
        return -1
    }

    private fun skipToEndOfLine() {
        while (pos < input.length) {
            val c = input[pos++]
            if (c == '\r' || c == '\n') return
        }
    }

    private fun nextString(quote: Char): String {
        val out = StringBuilder()
        while (pos < input.length) {
            val c = input[pos++]
            when (c) {
                quote -> return out.toString()
                '\\' -> {
                    if (pos == input.length) throw JSONException("Unterminated escape sequence")
                    when (val e = input[pos++]) {
                        'u' -> {
                            if (pos + 4 > input.length) throw JSONException("Unterminated escape sequence")
                            val hex = input.substring(pos, pos + 4)
                            pos += 4
                            out.append(hex.toIntOrNull(16)?.toChar() ?: throw JSONException("Invalid escape sequence: $hex"))
                        }
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        'f' -> out.append('\u000C')
                        else -> out.append(e)
                    }
                }
                else -> out.append(c)
            }
        }
        throw JSONException("Unterminated string")
    }

    private fun readLiteral(): Any {
        val start = pos
        while (pos < input.length && input[pos] !in LITERAL_ENDS) pos++
        val literal = input.substring(start, pos).trim()
        if (literal.isEmpty()) throw JSONException("Expected literal value at character $pos")
        if (literal.equals("null", ignoreCase = true)) return JSONObject.NULL
        if (literal.equals("true", ignoreCase = true)) return true
        if (literal.equals("false", ignoreCase = true)) return false
        if (!literal.contains('.')) {
            val number = when {
                literal.startsWith("0x") || literal.startsWith("0X") -> literal.substring(2).toLongOrNull(16)
                literal.startsWith("0") && literal.length > 1 -> literal.substring(1).toLongOrNull(8)
                else -> literal.toLongOrNull()
            }
            if (number != null) {
                return if (number in Int.MIN_VALUE..Int.MAX_VALUE) number.toInt() else number
            }
        }
        literal.toDoubleOrNull()?.let { return it }
        return literal
    }

    private fun readObject(): JSONObject {
        val result = JSONObject()
        if (nextClean() == '}'.code) return result
        pos--
        while (true) {
            val name = nextValue()
            if (name !is String) {
                throw JSONException("Names must be strings, but $name is of type ${name?.let { it::class.simpleName }}")
            }
            val separator = nextClean()
            if (separator != ':'.code && separator != '='.code) throw JSONException("Expected ':' after $name")
            if (separator == '='.code && pos < input.length && input[pos] == '>') pos++
            result.putStored(name, nextValue())
            when (nextClean()) {
                '}'.code -> return result
                ';'.code, ','.code -> continue
                else -> throw JSONException("Unterminated object at character $pos")
            }
        }
    }

    private fun readArray(): JSONArray {
        val result = JSONArray()
        var hasTrailingSeparator = false
        while (true) {
            when (nextClean()) {
                -1 -> throw JSONException("Unterminated array")
                ']'.code -> {
                    if (hasTrailingSeparator) result.put(null)
                    return result
                }
                ','.code, ';'.code -> {
                    result.put(null)
                    hasTrailingSeparator = true
                    continue
                }
                else -> pos--
            }
            result.put(nextValue())
            when (nextClean()) {
                ']'.code -> return result
                ','.code, ';'.code -> hasTrailingSeparator = true
                else -> throw JSONException("Unterminated array at character $pos")
            }
        }
    }

    private companion object {
        const val LITERAL_ENDS = "{}[]/\\:,=;# \t\u000C\r\n"
    }
}

// Parsed values go in as they are: a JSON null is kept as NULL rather than removing the name.
private fun JSONObject.putStored(name: String, value: Any?) {
    if (value == null) put(name, JSONObject.NULL) else put(name, value)
}

private fun checkDouble(d: Double) {
    if (d.isNaN() || d.isInfinite()) throw JSONException("Forbidden numeric value: $d")
}

private fun toStringValue(value: Any?): String? = when (value) {
    null -> null
    is String -> value
    else -> value.toString()
}

private fun toDoubleValue(value: Any?): Double? = when (value) {
    is Double -> value
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
}

private fun toIntValue(value: Any?): Int? = when (value) {
    is Int -> value
    is Number -> value.toInt()
    is String -> value.trim().toDoubleOrNull()?.toInt()
    else -> null
}

private fun toLongValue(value: Any?): Long? = when (value) {
    is Long -> value
    is Number -> value.toLong()
    is String -> value.trim().toDoubleOrNull()?.toLong()
    else -> null
}

private fun toBooleanValue(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is String -> when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        else -> null
    }
    else -> null
}

private fun typeMismatch(value: Any?, type: String) = JSONException("Value $value cannot be converted to $type")

private fun typeMismatch(name: Any, value: Any?, type: String) = JSONException("Value $value at $name cannot be converted to $type")

private fun writeValue(out: StringBuilder, value: Any?) {
    when (value) {
        null -> out.append("null")
        is JSONObject -> value.write(out)
        is JSONArray -> value.write(out)
        is String -> writeString(out, value)
        is Boolean -> out.append(value)
        is Double -> out.append(numberText(value))
        is Float -> out.append(numberText(value.toDouble()))
        is Number -> out.append(value.toString())
        else -> if (value === JSONObject.NULL) out.append("null") else writeString(out, value.toString())
    }
}

/** Whole numbers are written without ".0", as on Android. */
private fun numberText(d: Double): String {
    val asLong = d.toLong()
    return if (d == asLong.toDouble() && asLong != Long.MAX_VALUE && asLong != Long.MIN_VALUE) asLong.toString() else d.toString()
}

private fun writeString(out: StringBuilder, s: String) {
    out.append('"')
    for (c in s) {
        when (c) {
            '"', '\\', '/' -> out.append('\\').append(c)
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\u000C' -> out.append("\\f")
            else -> if (c.code <= 0x1F) {
                out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else {
                out.append(c)
            }
        }
    }
    out.append('"')
}
