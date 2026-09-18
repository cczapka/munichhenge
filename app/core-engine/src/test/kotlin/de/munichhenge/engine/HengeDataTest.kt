package de.munichhenge.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HengeDataTest {
    private val sightlinesJson = """
    {
    "version": 1,
    "generated": "2026-09-18",
    "params": {"min_length_m": 400.0, "merge_tol_deg": 8.0, "max_offset_m": 8.0, "default_building_h_m": 20.0, "eye_h_m": 1.7},
    "sightlines": [
    {"id":"f_axis","name":"Test Axis","kind":"axis","a":[48.15899,11.55],"b":[48.15899,11.5594],"length_m":699,"max_offset_m":0.0,"bearing_ab":90.0,"bearing_ba":270.0,"obstruction_toward_a_deg":1.5,"obstruction_toward_b_deg":3.0,"canyon":false,"featured":true,"notes":null,"osm_way_ids":[]},
    {"id":"sl_open_hill","name":"Hill","kind":"open_horizon","a":[48.15,11.55],"b":[48.15,11.55],"length_m":0,"max_offset_m":0.0,"bearing_ab":null,"bearing_ba":null,"obstruction_toward_a_deg":0.5,"obstruction_toward_b_deg":0.5,"canyon":false,"featured":true,"notes":"n","osm_way_ids":[]},
    {"id":"sl_x","name":"Teststraße","kind":"street","a":[48.15,11.55],"b":[48.15,11.57],"length_m":1486,"max_offset_m":0.4,"bearing_ab":90.0,"bearing_ba":270.0,"obstruction_toward_a_deg":0.62,"obstruction_toward_b_deg":0.62,"canyon":true,"featured":false,"notes":null,"osm_way_ids":[1,2,3]}
    ]}
    """.trimIndent()

    private val poisJson = """
    {
    "version": 1,
    "pois": [
    {"id":"poi_n1","name":"Café A","kind":"cafe","at":[48.15013,11.55054],"osm_id":"node/1","open_horizon":false,"views":[{"sightline_id":"sl_x","toward":"b","distance_m":15.0}],"tags":{"opening_hours":"Mo-Su 09:00-23:00"}},
    {"id":"s_hill","name":"Hill","kind":"viewpoint","at":[48.15,11.55],"osm_id":null,"open_horizon":true,"views":[{"sightline_id":"sl_open_hill","toward":"b","distance_m":0.0},{"sightline_id":"sl_x","toward":"b","distance_m":3.0}],"tags":{},"featured":true,"notes":null}
    ]}
    """.trimIndent()

    @Test
    fun `loads the pipeline schema`() {
        val d = HengeData.fromJson(sightlinesJson, poisJson)
        assertEquals(3, d.sightlines.size)
        assertEquals(2, d.pois.size)
        val x = d.sightline("sl_x")!!
        assertEquals(48.15, x.a.lat); assertEquals(11.57, x.b.lon)
        assertEquals(90.0, x.bearingAb); assertEquals(270.0, x.bearingBa)
        assertEquals(0.62, x.obstructionTowardBDeg)
        assertTrue(x.canyon); assertEquals(listOf(1L, 2L, 3L), x.osmWayIds)
        val hill = d.sightline("sl_open_hill")!!
        assertTrue(hill.openHorizon); assertNull(hill.bearingAb); assertEquals("n", hill.notes)
        val cafe = d.poi("poi_n1")!!
        assertEquals(Endpoint.B, cafe.views.single().toward)
        assertEquals("Mo-Su 09:00-23:00", cafe.tags["opening_hours"])
        assertEquals(false, cafe.featured)
        assertTrue(d.poi("s_hill")!!.featured && d.poi("s_hill")!!.openHorizon)
    }

    @Test
    fun `bearing and obstruction for a viewing direction`() {
        val d = HengeData.fromJson(sightlinesJson, poisJson)
        val f = d.sightline("f_axis")!!
        assertEquals(90.0, f.bearing(Endpoint.A)); assertEquals(3.0, f.obstruction(Endpoint.A))   // stand at A, look at B
        assertEquals(270.0, f.bearing(Endpoint.B)); assertEquals(1.5, f.obstruction(Endpoint.B))
        assertEquals(f.a, f.standingPoint(Endpoint.A)); assertEquals(f.b, f.standingPoint(Endpoint.B))
    }

    @Test
    fun `pois are indexed by sightline and direction`() {
        val d = HengeData.fromJson(sightlinesJson, poisJson)
        assertEquals(listOf("poi_n1", "s_hill"), d.poiIdsFor("sl_x", viewFrom = Endpoint.A))   // looking toward B
        assertEquals(emptyList(), d.poiIdsFor("sl_x", viewFrom = Endpoint.B))
        assertEquals(listOf("s_hill"), d.poiIdsFor("sl_open_hill", viewFrom = Endpoint.A))
    }
}
