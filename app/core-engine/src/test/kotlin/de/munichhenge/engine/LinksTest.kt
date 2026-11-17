package de.munichhenge.engine

import de.munichhenge.solar.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinksTest {
    private val cases = Json.parseToJsonElement(File("../../testdata/links_vectors.json").readText())
        .jsonObject["cases"]!!.jsonArray.map { it.jsonObject }

    private fun s(o: kotlinx.serialization.json.JsonObject, k: String) = o[k]!!.jsonPrimitive.content
    private fun d(o: kotlinx.serialization.json.JsonObject, k: String) = o[k]!!.jsonPrimitive.double

    @Test
    fun `shared vectors match the Python implementation`() {
        assertTrue(cases.size >= 5)
        for (c in cases) {
            val p = GeoPoint(d(c, "lat"), d(c, "lon"))
            assertEquals(s(c, "streetview"), Links.streetView(p, d(c, "bearing"), d(c, "obstruction")), s(c, "name"))
            assertEquals(s(c, "walk"), Links.walk(p), s(c, "name"))
            assertEquals(s(c, "geo"), Links.geo(p, s(c, "label")), s(c, "name"))
        }
    }

    @Test
    fun `a German default locale still writes dots`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(s(cases[0], "streetview"), Links.streetView(GeoPoint(48.14305, 11.59506), 287.0, 1.13))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `heading and pitch rules`() {
        assertEquals(0, Links.heading(359.6))
        assertEquals(0, Links.heading(-0.4))
        assertEquals(13, Links.heading(12.5))
        assertEquals(1, Links.pitch(0.4))
        assertEquals(1, Links.pitch(1.0))
        assertEquals(2, Links.pitch(1.01))
        assertEquals(10, Links.pitch(14.0))
        assertEquals(0, Links.pitch(-0.3))
    }

    @Test
    fun `a street direction looks from the standing point along its bearing`() {
        val sl = Synthetic.street("w", 270.0, obstruction = 1.4)
        val a = LinkTarget.forDirection(sl, Endpoint.A)
        assertEquals(sl.a, a.point)
        assertEquals(270.0, a.headingDeg)
        assertEquals(1.4, a.obstructionDeg)
        assertFalse(a.onWater)
        val b = LinkTarget.forDirection(sl, Endpoint.B)
        assertEquals(sl.b, b.point)
        assertEquals(90.0, b.headingDeg)
        assertTrue(Links.streetView(b).contains("&heading=90&pitch=2&"))
    }

    @Test
    fun `water ends are flagged, bridge decks are not`() {
        val canal = Synthetic.street("c", 87.0).copy(kind = "canal")
        assertTrue(LinkTarget.forDirection(canal, Endpoint.A).onWater)
        val bridge = Synthetic.street("br", 210.0).copy(kind = "bridge")
        assertFalse(LinkTarget.forDirection(bridge, Endpoint.A).onWater, "A is the crossing point on the deck")
        assertTrue(LinkTarget.forDirection(bridge, Endpoint.B).onWater, "B lies on the river")
    }

    @Test
    fun `an open-horizon spot looks toward the azimuth it is given`() {
        val hill = Synthetic.openHorizon("sl_open_hill")
        val t = LinkTarget.forOpenHorizon(hill, 243.0)
        assertEquals(hill.a, t.point)
        assertEquals(243.0, t.headingDeg)
        assertEquals(0.5, t.obstructionDeg)
    }

    @Test
    fun `a spot looks from its own position along the view's direction`() {
        val sl = Synthetic.street("w", 270.0, obstruction = 0.8)
        val poi = Synthetic.poi("cafe", View("w", Endpoint.B, 12.0)).copy(at = GeoPoint(48.1401, 11.5795))
        val t = LinkTarget.forPoiView(poi, poi.views.single(), sl)
        assertEquals(poi.at, t.point)
        assertEquals(270.0, t.headingDeg, "looking toward B means the A->B bearing")
        assertEquals(0.8, t.obstructionDeg)
        val back = LinkTarget.forPoiView(poi, View("w", Endpoint.A, 12.0), sl)
        assertEquals(90.0, back.headingDeg)
    }
}
