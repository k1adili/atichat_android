package com.k1adili.atichat

import com.k1adili.atichat.core.Jalali
import com.k1adili.atichat.core.Json
import com.k1adili.atichat.core.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JalaliTest {
    @Test
    fun knownDates() {
        assertEquals(Jalali.Date(1405, 8, 23), Jalali.fromGregorian(LocalDate.of(2026, 11, 14)))
        assertEquals(Jalali.Date(1399, 1, 1), Jalali.fromGregorian(LocalDate.of(2020, 3, 20)))
        assertEquals(Jalali.Date(1403, 12, 30), Jalali.fromGregorian(LocalDate.of(2025, 3, 20)))
    }

    @Test
    fun roundTripOverTwoCenturies() {
        var date = LocalDate.of(1900, 1, 1)
        val end = LocalDate.of(2100, 12, 31)
        while (!date.isAfter(end)) {
            val j = Jalali.fromGregorian(date)
            assertEquals("round trip failed for $date", date, Jalali.toGregorian(j.year, j.month, j.day))
            date = date.plusDays(1)
        }
    }

    @Test
    fun persianDigits() {
        assertEquals("۱۴۰۵/۰۸/۲۳", Jalali.toPersianDigits("1405/08/23"))
    }
}

class JsonTest {
    @Test
    fun parsesUnicodeEscapesAndTypes() {
        val map = Json.parseObject("{\"a\":\"\\u0633\\u0644\\u0627\\u0645 \\ud83d\\udc4b\",\"n\":37021,\"b\":true,\"x\":null}")
        assertEquals("سلام 👋", map["a"])
        assertEquals(37021L, map["n"])
        assertEquals(true, map["b"])
        assertNull(map["x"])
    }

    @Test
    fun roundTrip() {
        val original = mapOf("t" to "line1\nline2 \"q\" \\ \u0001", "n" to 5L, "l" to listOf(1L, 2L))
        assertEquals(original, Json.parseObject(Json.encode(original)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGarbage() {
        Json.parseObject("{\"a\":")
    }
}

class ProtocolTest {
    private val fingerprint = "a".repeat(64)

    @Test
    fun acceptsValidPresence() {
        val packet = Json.parseObject(Json.encode(Protocol.presencePacket("id-1", "علی", "", 37021, fingerprint)))
        val peer = Protocol.parsePresence(packet, "192.168.1.20", 0L)
        assertNotNull(peer)
        assertEquals(37021, peer!!.tcpPort)
        assertEquals("علی", peer.username)
    }

    @Test
    fun rejectsBadPresence() {
        fun parse(json: String) = Protocol.parsePresence(Json.parseObject(json), "1.2.3.4", 0L)
        assertNull(parse("{\"user_id\":\"x\",\"username\":\" \",\"tcp_port\":37021,\"fingerprint\":\"$fingerprint\"}"))
        assertNull(parse("{\"user_id\":\"x\",\"username\":\"a\",\"tcp_port\":70000,\"fingerprint\":\"$fingerprint\"}"))
        assertNull(parse("{\"user_id\":\"x\",\"username\":\"a\",\"tcp_port\":37021,\"fingerprint\":\"XYZ\"}"))
        assertNull(parse("{\"user_id\":\"x\",\"username\":\"a\",\"tcp_port\":37021,\"fingerprint\":\"$fingerprint\",\"avatar\":5}"))
    }

    @Test
    fun sanitizesFileNames() {
        assertEquals("evil___.txt", Protocol.sanitizeFileName("..\\..\\evil<>:.txt"))
        assertEquals("c.pdf", Protocol.sanitizeFileName("C:/a/b/c.pdf"))
        assertEquals("received-file", Protocol.sanitizeFileName("  ... "))
    }

    @Test
    fun longNamesKeepExtensionAndFitFileSystemLimit() {
        val name = Protocol.sanitizeFileName("گ".repeat(200) + ".mp4")
        assertTrue(name.toByteArray().size <= Protocol.MAX_STORED_NAME_BYTES)
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun timestampFormatMatchesPython() {
        val ts = Protocol.nowTimestamp()
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}\\+00:00").matches(ts))
        assertEquals(Protocol.parseTimestamp(ts), Protocol.parseTimestamp(ts))
    }
}
