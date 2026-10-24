package com.pokai.codync.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val vectors: JsonObject by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("remote-relay-vectors.json")!!
            .bufferedReader().readText()
        json.parseToJsonElement(text).jsonObject
    }

    private fun obj(key: String) = vectors[key]!!.jsonObject
    private fun str(o: JsonObject, key: String) = o[key]!!.jsonPrimitive.content

    @Test
    fun keysAndComputerId() {
        val k = obj("keys")
        val host = Keys.ed25519FromSeed(Codec.unb64n(str(k, "hostSignSeed"), 32))
        val device = Keys.ed25519FromSeed(Codec.unb64n(str(k, "deviceSignSeed"), 32))
        assertEquals(str(k, "hostSignPub"), Codec.b64(Keys.ed25519Public(host)))
        assertEquals(str(k, "deviceSignPub"), Codec.b64(Keys.ed25519Public(device)))
        assertEquals(str(k, "computerId"), Codec.computerId(Keys.ed25519Public(host)))
        val hostBox = Keys.x25519FromSeed(Codec.unb64n(str(k, "hostBoxPriv"), 32))
        assertEquals(str(k, "hostBoxPub"), Codec.b64(Keys.x25519Public(hostBox)))
    }

    @Test
    fun offerId() {
        val p = obj("pairing")
        assertEquals(str(p, "offerId"), PairingCrypto.offerId(Codec.unb64n(str(p, "code"), 16)))
    }

    @Test
    fun requestSig() {
        val k = obj("keys")
        val r = obj("requestSig")
        val device = Keys.ed25519FromSeed(Codec.unb64n(str(k, "deviceSignSeed"), 32))
        assertEquals(str(r, "bodySha256"), CodyncSig.bodySha256B64(ByteArray(0)))
        val input = CodyncSig.canonical(
            method = str(r, "method"),
            authority = str(r, "authority"),
            pathAndQuery = str(r, "pathAndQuery"),
            ts = r["ts"]!!.jsonPrimitive.long,
            nonce = str(r, "nonce"),
            body = ByteArray(0),
        )
        assertEquals(str(r, "canonical"), input)
        val header = "Codync-Sig: " + CodyncSig.header(device, r["ts"]!!.jsonPrimitive.long, str(r, "nonce"), input)
        assertEquals(str(r, "header"), header)
    }

    @Test
    fun claimAndSas() {
        val k = obj("keys")
        val c = obj("claim")
        val host = Keys.ed25519FromSeed(Codec.unb64n(str(k, "hostSignSeed"), 32))
        val input = PairingCrypto.claimInput(
            str(c, "claimId"), str(c, "nonce"), str(c, "userId"), str(c, "computerId"), str(c, "boxKey"),
        )
        assertEquals(str(c, "canonical"), input)
        assertEquals(str(c, "sig"), Codec.b64(Keys.ed25519Sign(host, input.toByteArray(Charsets.UTF_8))))

        val s = obj("sas")
        val device = Keys.ed25519FromSeed(Codec.unb64n(str(k, "deviceSignSeed"), 32))
        val dk = Keys.ed25519Public(device)
        assertEquals(str(s, "commit"), Codec.b64(PairingCrypto.sasCommit(dk, Codec.unb64n(str(s, "deviceNonce"), 32))))
        assertEquals(
            str(s, "code"),
            PairingCrypto.sasCode(
                Keys.ed25519Public(host),
                dk,
                Codec.unb64n(str(s, "deviceNonce"), 32),
                Codec.unb64n(str(s, "hostNonce"), 32),
            ),
        )
    }

    @Test
    fun handshakeAndFrames() {
        val k = obj("keys")
        val h = obj("handshake")
        val host = Keys.ed25519FromSeed(Codec.unb64n(str(k, "hostSignSeed"), 32))
        val device = Keys.ed25519FromSeed(Codec.unb64n(str(k, "deviceSignSeed"), 32))
        val cidRaw = Codec.computerIdRaw(Keys.ed25519Public(host))
        val dk = Keys.ed25519Public(device)
        val ekD = Codec.unb64n(str(h, "deviceEphPub"), 32)
        val n = Codec.unb64n(str(h, "n"), 32)
        val hs1 = ChannelCrypto.hs1Input(cidRaw, dk, ekD, n)
        assertEquals(str(h, "hs1SignInput"), Codec.b64(hs1))
        assertEquals(str(h, "hs1Sig"), Codec.b64(Keys.ed25519Sign(device, hs1)))

        val ekH = Codec.unb64n(str(h, "hostEphPub"), 32)
        val th = ChannelCrypto.transcriptHash(cidRaw, dk, ekD, n, ekH)
        assertEquals(str(h, "transcriptHash"), Codec.b64(th))

        val deviceEph = Keys.x25519FromSeed(Codec.unb64n(str(h, "deviceEphPriv"), 32))
        val shared = Keys.x25519(deviceEph, ekH)
        assertEquals(str(h, "sharedSecret"), Codec.b64(shared))

        val keys = ChannelCrypto.channelKeys(shared, th)
        assertEquals(str(h, "kD2H"), Codec.b64(keys.d2h))
        assertEquals(str(h, "kH2D"), Codec.b64(keys.h2d))

        vectors["frames"]!!.jsonArray.forEachIndexed { i, el ->
            val f = el.jsonObject
            val key = if (str(f, "dir") == "d2h") keys.d2h else keys.h2d
            val flagged = Codec.unb64(str(f, "plaintext"))
            val chunk = flagged.copyOfRange(1, flagged.size)
            val sealed = ChannelCrypto.sealFrame(key, f["c"]!!.jsonPrimitive.long, f["final"]!!.jsonPrimitive.boolean, chunk)
            assertEquals("frame $i", str(f, "d"), Codec.b64(sealed))
            val (last, opened) = ChannelCrypto.openFrame(key, f["c"]!!.jsonPrimitive.long, sealed)
            assertEquals(f["final"]!!.jsonPrimitive.boolean, last)
            assertEquals(String(chunk, Charsets.UTF_8), String(opened, Charsets.UTF_8))
        }
    }
}
