package com.pokai.codync.transport

import com.pokai.codync.crypto.Keys
import com.pokai.codync.model.Bot
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in check against a real host: `CODYNC_PAIR_URL='codync-dev://pair?...' ./gradlew :core:transport:test`.
 * CI leaves it skipped. It pairs a throwaway device, opens the encrypted channel and waits for the
 * host's catch-up roster, which is the exact path the app uses after scanning a QR code.
 */
class RealHostIntegrationTest {
    @Test
    fun pairAndReceiveRoster() = runBlocking {
        val raw = System.getenv("CODYNC_PAIR_URL")
        assumeTrue("Set CODYNC_PAIR_URL to run against a real host", !raw.isNullOrBlank())
        val offer = requireNotNull(PairUrl.parse(raw)) { "CODYNC_PAIR_URL isn't a Codync pairing link" }
        val deviceKey = Keys.ed25519FromSeed(Keys.randomBytes(32))
        val computer = DirectPairingClient.pair(offer, deviceKey, "Android transport check")
        assertEquals(offer.computerId, computer.id)
        assertTrue(computer.urls.isNotEmpty() || !computer.cloud.isNullOrBlank())

        val transport = DirectChannelTransport(deviceKey, "Android transport check")
        try {
            val hello = transport.connect(computer).getOrThrow()
            assertEquals(computer.id, hello.computerId)
            val bots = withTimeout(20_000) {
                transport.events.filterIsInstance<HostEvent.BotUpsert>()
                    .first()
                    .bot
            }
            assertTrue(bots.name.isNotBlank())
        } finally {
            transport.disconnect()
        }
    }
}
