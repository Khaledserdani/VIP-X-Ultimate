package com.vipx.ultimate

import org.junit.Assert.*
import org.junit.Test

class SheerGarmentCatalogTest {
    @Test fun catalogContainsModernStylesAndColorChoices() {
        assertTrue(SheerGarmentCatalog.presets.size >= 5)
        assertTrue(ModernGarmentColor.values().size >= 10)
        assertEquals(
            setOf(
                SheerGarmentStyle.CHIFFON_OVERLAY,
                SheerGarmentStyle.TULLE_LAYERED,
                SheerGarmentStyle.LACE_OVERLAY,
                SheerGarmentStyle.ORGANZA_LAYER,
                SheerGarmentStyle.MESH_PANEL
            ),
            SheerGarmentCatalog.presets.map { it.style }.toSet()
        )
    }

    @Test fun everySheerPresetUsesOpaqueLiningCoverage() {
        assertTrue(SheerGarmentCatalog.presets.all {
            SheerGarmentCatalog.validateForPreview(it)
        })
    }

    @Test fun catalogLookupReturnsMatchingPreset() {
        val preset = SheerGarmentCatalog.byId("lace_sleeve")
        assertNotNull(preset)
        assertEquals(LiningCoverage.OPAQUE_BODY_WITH_SHEER_SLEEVES, preset!!.lining)
    }
}
