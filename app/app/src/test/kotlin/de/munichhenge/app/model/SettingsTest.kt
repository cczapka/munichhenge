package de.munichhenge.app.model

import de.munichhenge.app.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    @Test
    fun `defaults match the plan`() {
        val s = Settings()
        assertEquals(0.7, s.minQuality); assertEquals(60, s.leadMinutes)
        val c = s.toEngineConfig()
        assertEquals(0.5, c.perfectDeg); assertEquals(1.5, c.goodDeg); assertEquals(3.0, c.nearDeg)
        assertEquals(0.0, c.obstructionOffsetDeg); assertEquals(0.0, c.canyonBonus)
    }

    @Test
    fun `tolerance bands stay ordered and values clamped`() {
        val s = Settings(perfectDeg = 2.0, goodDeg = 1.0, nearDeg = 0.8, minQuality = 1.4, leadMinutes = -5).normalized()
        assertEquals(0.8, s.nearDeg); assertEquals(0.8, s.goodDeg); assertEquals(0.8, s.perfectDeg)
        assertEquals(1.0, s.minQuality); assertEquals(0, s.leadMinutes)
        assertEquals(5.0, Settings(obstructionOffsetDeg = 9.0).normalized().obstructionOffsetDeg)
    }
}
