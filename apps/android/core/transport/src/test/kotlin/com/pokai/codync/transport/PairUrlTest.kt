package com.pokai.codync.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairUrlTest {
    @Test
    fun parsesCodyncPairLink() {
        val offer = PairUrl.parse(
            "codync://pair?v=3&computerId=NHUPmL1Z_PyUbaRaqr6TOw&signKey=abc&boxKey=def&name=Studio&code=xyz&urls=http://192.168.1.2:19223,https://relay.example",
        )
        assertNotNull(offer)
        assertEquals("NHUPmL1Z_PyUbaRaqr6TOw", offer!!.computerId)
        assertEquals("abc", offer.signKey)
        assertEquals("Studio", offer.name)
        assertEquals(2, offer.urls.size)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(PairUrl.parse("hello world"))
        assertNull(PairUrl.parse(""))
    }
}
