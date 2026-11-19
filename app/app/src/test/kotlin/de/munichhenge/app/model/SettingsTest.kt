package de.munichhenge.app.model

import de.munichhenge.app.settings.Settings
import de.munichhenge.engine.EngineConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    @Test
    fun `defaults match the engine`() {
        val s = Settings()
        assertEquals(0.7, s.minQuality); assertEquals(60, s.leadMinutes)
        val c = s.toEngineConfig()
        val d = EngineConfig()
        assertEquals(d.assumedStreetWidthM, c.assumedStreetWidthM)
        assertEquals(d.maxHeightAboveFloorDeg, c.maxHeightAboveFloorDeg)
        assertEquals(0.0, c.obstructionOffsetDeg)
    }

    @Test
    fun `values are clamped`() {
        val s = Settings(minQuality = 1.4, leadMinutes = -5, streetWidthM = 200.0, maxHeightAboveFloorDeg = 0.1,
            obstructionOffsetDeg = 9.0).normalized()
        assertEquals(1.0, s.minQuality); assertEquals(0, s.leadMinutes)
        assertEquals(60.0, s.streetWidthM); assertEquals(1.0, s.maxHeightAboveFloorDeg); assertEquals(5.0, s.obstructionOffsetDeg)
        assertEquals(60.0, s.toEngineConfig().assumedStreetWidthM)
    }
}
