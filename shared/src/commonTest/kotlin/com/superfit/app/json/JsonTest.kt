package com.superfit.app.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonTest {

    @Test
    fun readsAnAiMealAnswer() {
        val o = JSONObject("""{"foodText":"2 roti","calories":240.5,"protein":8,"items":[{"name":"roti","quantity":2}]}""")
        assertEquals("2 roti", o.optString("foodText"))
        assertEquals(240.5, o.optDouble("calories", 0.0))
        assertEquals(8.0, o.optDouble("protein", 0.0))
        val items = o.optJSONArray("items")!!
        assertEquals(1, items.length())
        assertEquals("roti", items.getJSONObject(0).getString("name"))
        assertEquals(2, items.getJSONObject(0).optInt("quantity"))
    }

    @Test
    fun numbersWrittenAsTextStillReadAsNumbersAndTheReverse() {
        val o = JSONObject("""{"a":"12.5","b":" 7 ","c":3,"d":"x","e":"TRUE"}""")
        assertEquals(12.5, o.optDouble("a"))
        assertEquals(7, o.optInt("b"))
        assertEquals("3", o.optString("c"))
        assertTrue(o.optDouble("d").isNaN())
        assertEquals(0, o.optInt("d"))
        assertTrue(o.optBoolean("e"))
        assertEquals(12, o.optInt("a"))
    }

    @Test
    fun missingValuesUseTheFallback() {
        val o = JSONObject("{}")
        assertEquals("", o.optString("x"))
        assertEquals("fb", o.optString("x", "fb"))
        assertEquals(1.5, o.optDouble("x", 1.5))
        assertEquals(4, o.optInt("x", 4))
        assertFalse(o.optBoolean("x"))
        assertNull(o.optJSONObject("x"))
        assertNull(o.optJSONArray("x"))
        assertFailsWith<JSONException> { o.getString("x") }
    }

    @Test
    fun jsonNullBehavesLikeAndroid() {
        val o = JSONObject("""{"a":null}""")
        assertTrue(o.has("a"))
        assertTrue(o.isNull("a"))
        assertTrue(o.isNull("missing"))
        assertEquals("null", o.optString("a", "fb"))
        assertTrue(o.optDouble("a").isNaN())
        assertEquals("""{"a":null}""", o.toString())
    }

    @Test
    fun forgivingInputReadsLikeAndroid() {
        val o = JSONObject("""{'name': 'dal', unit = bowl; /* note */ "n" => 2} ``` trailing text""")
        assertEquals("dal", o.optString("name"))
        assertEquals("bowl", o.optString("unit"))
        assertEquals(2, o.optInt("n"))
        val a = JSONArray("[1, 2,]")
        assertEquals(3, a.length())
        assertTrue(a.isNull(2))
    }

    @Test
    fun brokenInputFails() {
        assertFailsWith<JSONException> { JSONObject("") }
        assertFailsWith<JSONException> { JSONObject("[1]") }
        assertFailsWith<JSONException> { JSONObject("""{"a":1""") }
        assertFailsWith<JSONException> { JSONArray("""{"a":1}""") }
    }

    @Test
    fun putNullRemovesAndCollectionsBecomeArrays() {
        val o = JSONObject().put("a", 1).put("b", "x")
        o.put("a", null)
        assertFalse(o.has("a"))
        o.put("list", JSONArray(listOf("p", "q")))
        assertEquals("""{"b":"x","list":["p","q"]}""", o.toString())
        assertFailsWith<JSONException> { o.put("bad", Double.NaN) }
    }

    @Test
    fun writesWholeNumbersWithoutDecimalsAndEscapesText() {
        val o = JSONObject().put("kcal", 120.0).put("g", 2.5).put("n", 3).put("t", "a \"b\"\n/c")
        assertEquals("""{"kcal":120,"g":2.5,"n":3,"t":"a \"b\"\n\/c"}""", o.toString())
        val back = JSONObject(o.toString())
        assertEquals("a \"b\"\n/c", back.getString("t"))
        assertEquals(120.0, back.getDouble("kcal"))
    }

    @Test
    fun keepsTheOrderNamesWereAddedIn() {
        val o = JSONObject("""{"z":1,"a":2,"m":3}""")
        assertEquals(listOf("z", "a", "m"), o.keys().asSequence().toList())
    }

    @Test
    fun tokenerReturnsWhateverValueComesFirst() {
        assertTrue(JSONTokener("""[{"name":"x"}]""").nextValue() is JSONArray)
        assertTrue(JSONTokener("""{"name":"x"}""").nextValue() is JSONObject)
        assertEquals("hello", JSONTokener("\"hello\"").nextValue())
        assertEquals(42, JSONTokener("42").nextValue())
        assertEquals(1.5, JSONTokener("1.5").nextValue())
    }

    @Test
    fun readsUnicodeEscapes() {
        assertEquals("é", JSONObject("""{"a":"é"}""").getString("a"))
    }
}
