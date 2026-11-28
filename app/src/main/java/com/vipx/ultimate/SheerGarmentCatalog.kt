package com.vipx.ultimate

/** Modern sheer-look garments. All transparent-effect fabrics require an opaque lining. */
internal enum class SheerGarmentStyle {
    CHIFFON_OVERLAY,
    TULLE_LAYERED,
    LACE_OVERLAY,
    ORGANZA_LAYER,
    MESH_PANEL
}

internal enum class ModernGarmentColor(val hex: String) {
    BLACK("#171717"),
    WHITE("#FFFFFF"),
    IVORY("#F3EBDD"),
    CHAMPAGNE("#D8C1A0"),
    DUSTY_ROSE("#C98F98"),
    BURGUNDY("#6E1835"),
    EMERALD("#08745B"),
    SAPPHIRE("#174A82"),
    LAVENDER("#A99ACB"),
    POWDER_BLUE("#A9C9DA"),
    SILVER_GREY("#AEB4BC"),
    MOCHA("#80604D"),
    TERRACOTTA("#B9654B")
}

internal enum class LiningCoverage {
    FULL_OPAQUE,
    OPAQUE_BODY_WITH_SHEER_SLEEVES,
    OPAQUE_BASE_WITH_SHEER_OUTER_LAYER
}

internal data class SheerGarmentPreset(
    val id: String,
    val displayNameArabic: String,
    val style: SheerGarmentStyle,
    val color: ModernGarmentColor,
    val lining: LiningCoverage,
    val modernCut: String,
    val suitableMotion: Set<String>
) {
    init {
        require(id.isNotBlank() && displayNameArabic.isNotBlank())
        require(modernCut.isNotBlank())
        require(suitableMotion.isNotEmpty())
    }
}

internal object SheerGarmentCatalog {
    val presets = listOf(
        SheerGarmentPreset(
            "chiffon_abaya", "عباءة شيفون بطبقة خارجية انسيابية",
            SheerGarmentStyle.CHIFFON_OVERLAY, ModernGarmentColor.CHAMPAGNE,
            LiningCoverage.OPAQUE_BASE_WITH_SHEER_OUTER_LAYER,
            "قصة مستقيمة واسعة مع أكمام منسدلة",
            setOf("walk", "turn", "sit", "raise_arms")
        ),
        SheerGarmentPreset(
            "tulle_evening", "فستان سهرة بطبقات تول",
            SheerGarmentStyle.TULLE_LAYERED, ModernGarmentColor.BURGUNDY,
            LiningCoverage.FULL_OPAQUE,
            "قصة A بطبقات خفيفة فوق بطانة كاملة",
            setOf("walk", "turn", "sit")
        ),
        SheerGarmentPreset(
            "lace_sleeve", "قميص دانتيل بأكمام شفافة المظهر",
            SheerGarmentStyle.LACE_OVERLAY, ModernGarmentColor.IVORY,
            LiningCoverage.OPAQUE_BODY_WITH_SHEER_SLEEVES,
            "قصة عصرية مريحة مع جسم مبطن وأكمام دانتيل",
            setOf("walk", "turn", "raise_arms")
        ),
        SheerGarmentPreset(
            "organza_jacket", "سترة أورغانزا بطبقة مزدوجة",
            SheerGarmentStyle.ORGANZA_LAYER, ModernGarmentColor.EMERALD,
            LiningCoverage.OPAQUE_BASE_WITH_SHEER_OUTER_LAYER,
            "سترة قصيرة واسعة بحواف هندسية",
            setOf("walk", "turn", "raise_arms")
        ),
        SheerGarmentPreset(
            "mesh_sport", "قطعة رياضية شبكية ببطانة كاملة",
            SheerGarmentStyle.MESH_PANEL, ModernGarmentColor.BLACK,
            LiningCoverage.FULL_OPAQUE,
            "قصّة رياضية مرنة مع ألواح شبكية فوق البطانة",
            setOf("walk", "turn", "sit", "raise_arms")
        )
    )

    fun byId(id: String): SheerGarmentPreset? = presets.firstOrNull { it.id == id }

    fun validateForPreview(preset: SheerGarmentPreset): Boolean =
        preset.lining in setOf(
            LiningCoverage.FULL_OPAQUE,
            LiningCoverage.OPAQUE_BODY_WITH_SHEER_SLEEVES,
            LiningCoverage.OPAQUE_BASE_WITH_SHEER_OUTER_LAYER
        )
}
