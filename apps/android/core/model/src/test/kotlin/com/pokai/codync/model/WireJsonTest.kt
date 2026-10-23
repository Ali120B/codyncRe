package com.pokai.codync.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireJsonTest {

    @Serializable
    data class Sample(
        val name: String,
        val age: Int = 0,
        val optional: String? = null,
    )

    @Test
    fun testCompatibilityConstants() {
        assertEquals("3.0.0", Compatibility.APP_VERSION)
        assertEquals("3.0.0", Compatibility.MIN_HOST_VERSION)
        assertEquals(1, Compatibility.PROTOCOL_VERSION)
    }

    @Test
    fun testWireJsonIgnoresUnknownKeys() {
        val raw = """{"name":"Atlas","extraField":"ignored","age":42}"""
        val decoded = WireJson.json.decodeFromString<Sample>(raw)
        assertEquals("Atlas", decoded.name)
        assertEquals(42, decoded.age)
    }

    @Test
    fun testWireJsonSerializesDefaults() {
        val sample = Sample(name = "Echo")
        val encoded = WireJson.json.encodeToString(sample)
        assertTrue(encoded.contains("\"age\":0"))
    }
}
