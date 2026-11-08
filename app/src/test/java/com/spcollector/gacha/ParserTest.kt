package com.spcollector.gacha

import com.spcollector.gacha.ocr.*
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private val known = mapOf("Naruto Uzumaki" to "SSR", "Sasuke Uchiha" to "SP")

    @Test fun matchesNamesAndExplicitNumbers() {
        val r = ScreenshotParser.parse(
            listOf(OcrLine("#12 Sasuke Uchiha SP", 0.9f), OcrLine("Naruto Uzumaki", 0.8f), OcrLine("Riwayat Gacha", 0.9f)), known)
        assertEquals(2, r.size)
        assertEquals("Sasuke Uchiha", r[0].characterName); assertEquals(12, r[0].pullNumber); assertEquals("SP", r[0].rarity)
        assertEquals("Naruto Uzumaki", r[1].characterName); assertNull(r[1].pullNumber); assertEquals("SSR", r[1].rarity)
    }

    @Test fun toleratesSmallOcrErrors() {
        val r = ScreenshotParser.parse(listOf(OcrLine("Sasuke Uchlha SP", 1f)), known)
        assertEquals("Sasuke Uchiha", r.single().characterName)
    }

    @Test fun unreadableNameBecomesUnknown() {
        val r = ScreenshotParser.parse(listOf(OcrLine("#3 Zzqxv Plmk R", 0.95f)), known).single()
        assertTrue(r.unknown); assertNull(r.characterName); assertEquals(3, r.pullNumber); assertTrue(r.confidence <= 0.4f)
    }
}
