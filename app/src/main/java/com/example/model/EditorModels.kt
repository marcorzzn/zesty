package com.example.model

import android.graphics.Bitmap
import android.graphics.PorterDuff
import androidx.compose.ui.graphics.BlendMode

enum class LayerType {
    RASTER, IMAGE, SHAPE, TEXT, PATH
}

enum class ShapeKind(val label: String) {
    RECT("Rettangolo"),
    CIRCLE("Cerchio"),
    TRIANGLE("Triangolo"),
    POLYGON("Poligono"),
    STAR("Stella"),
    HEART("Cuore"),
    LINE("Linea"),
    CALLOUT("Chiamata")
}

enum class BlendOption(
    val id: String,
    val label: String,
    val composeBlend: BlendMode,
    val porterDuff: PorterDuff.Mode?
) {
    NORMAL("source-over", "Normale", BlendMode.SrcOver, PorterDuff.Mode.SRC_OVER),
    MULTIPLY("multiply", "Moltiplica", BlendMode.Multiply, PorterDuff.Mode.MULTIPLY),
    SCREEN("screen", "Scherma", BlendMode.Screen, PorterDuff.Mode.SCREEN),
    OVERLAY("overlay", "Sovrapponi", BlendMode.Overlay, PorterDuff.Mode.OVERLAY),
    DARKEN("darken", "Scurisci", BlendMode.Darken, PorterDuff.Mode.DARKEN),
    LIGHTEN("lighten", "Schiarisci", BlendMode.Lighten, PorterDuff.Mode.LIGHTEN),
    COLOR_DODGE("color-dodge", "Scherma colore", BlendMode.ColorDodge, null),
    COLOR_BURN("color-burn", "Brucia colore", BlendMode.ColorBurn, null),
    HARD_LIGHT("hard-light", "Luce intensa", BlendMode.Hardlight, null),
    SOFT_LIGHT("soft-light", "Luce soffusa", BlendMode.Softlight, null),
    DIFFERENCE("difference", "Differenza", BlendMode.Difference, null),
    EXCLUSION("exclusion", "Esclusione", BlendMode.Exclusion, null),
    HUE("hue", "Tonalità", BlendMode.Hue, null),
    SATURATION("saturation", "Saturazione", BlendMode.Saturation, null),
    COLOR("color", "Colore", BlendMode.Color, null),
    LUMINOSITY("luminosity", "Luminosità", BlendMode.Luminosity, null);

    companion object {
        fun fromId(id: String): BlendOption = entries.find { it.id == id } ?: NORMAL
    }
}

data class AdjustParams(
    val brightness: Float = 0f,     // -100..100
    val contrast: Float = 0f,       // -100..100
    val saturation: Float = 0f,     // -100..100
    val hue: Float = 0f,            // 0..360
    val temperature: Float = 0f,    // -100..100
    val highlights: Float = 0f,     // -100..100
    val shadows: Float = 0f,        // -100..100
    val clarity: Float = 0f,        // -100..100
    val exposure: Float = 0f        // -100..100
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f &&
                hue == 0f && temperature == 0f && highlights == 0f &&
                shadows == 0f && clarity == 0f && exposure == 0f
}

data class EffectConfig(
    val enabled: Boolean = false,
    val intensity: Float = 50f,     // 0..100
    val param1: Float = 30f,        // e.g. radius / shift / size
    val param2: Float = 50f,        // e.g. spread / blocks / angle
    val colorHex: Int = 0xFF00F0FF.toInt(),
    val localMask: Bitmap? = null,
    val localInverted: Boolean = false,
    val localizing: Boolean = false
)

data class EffectFilter(
    val id: String,
    val name: String,
    val label: String,
    val category: String = "FX",
    val defaultFade: Float = 0f,
    val defaultBlendMode: String = "Normal",
    val algorithm: String = "color_matrix",
    val jsonFile: String? = null
)

data class CurvePoint(val x: Float, val y: Float) // 0..1 normalized

data class CurvesData(
    val rgb: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val r: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val g: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val b: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
) {
    val isIdentity: Boolean
        get() = isDefault(rgb) && isDefault(r) && isDefault(g) && isDefault(b)

    private fun isDefault(pts: List<CurvePoint>): Boolean =
        pts.size == 2 && pts[0].x == 0f && pts[0].y == 0f && pts[1].x == 1f && pts[1].y == 1f
}

data class NormPoint(val x: Float, val y: Float) // 0..1 relative to layer w/h

data class EditorLayer(
    val id: String,
    val type: LayerType,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val selectedForMulti: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 800f,
    val h: Float = 800f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val blend: BlendOption = BlendOption.NORMAL,
    val bitmap: Bitmap? = null,
    val maskBitmap: Bitmap? = null,
    val maskInverted: Boolean = false,
    val editingMask: Boolean = false,
    val adjust: AdjustParams = AdjustParams(),
    val curves: CurvesData = CurvesData(),
    val effects: Map<String, EffectConfig> = emptyMap(),
    // 170 Filter FX properties
    val activeFilterId: String? = null,
    val activeFilterFade: Float = 0f,
    val activeFilterBlend: String = "Normal",
    // Shape properties
    val shapeKind: ShapeKind = ShapeKind.CIRCLE,
    val fillColor: Int = 0xFF8B5CF6.toInt(),
    val useGradient: Boolean = false,
    val gradColor2: Int = 0xFFE0289A.toInt(),
    val gradAngle: Float = 45f,
    val strokeColor: Int = 0xFFFFFFFF.toInt(),
    val strokeWidth: Float = 4f,
    val cornerRadius: Float = 16f,
    val polygonSides: Int = 5,
    val starPoints: Int = 5,
    val starInnerRatio: Float = 0.45f,
    // Shadow properties
    val shadowBlur: Float = 0f,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val shadowColor: Int = 0xAA000000.toInt(),
    // Text properties
    val text: String = "Doppio tocco per modificare",
    val fontFamily: String = "Inter",
    val fontSize: Float = 46f,
    val fontWeightBold: Boolean = true,
    val textAlign: Int = 1, // 0=left, 1=center, 2=right
    val lineHeight: Float = 1.2f,
    val textBend: Float = 0f, // -100..100
    val textOnPathLayerId: String? = null,
    // Vector Path properties
    val pathPoints: List<NormPoint> = emptyList(),
    val pathClosed: Boolean = true
) {
    fun deepCopy(): EditorLayer = copy(
        bitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true),
        maskBitmap = maskBitmap?.copy(Bitmap.Config.ARGB_8888, true),
        effects = effects.mapValues { (_, cfg) ->
            cfg.copy(localMask = cfg.localMask?.copy(Bitmap.Config.ARGB_8888, true))
        },
        pathPoints = pathPoints.toList(),
        curves = curves.copy(
            rgb = curves.rgb.toList(),
            r = curves.r.toList(),
            g = curves.g.toList(),
            b = curves.b.toList()
        )
    )
}

data class HistorySnapshot(
    val canvasW: Int,
    val canvasH: Int,
    val bgColor: Int,
    val fitRatioLabel: String,
    val fitBgMode: Int, // 0=solid, 1=pattern, 2=blur
    val borderOuter: Float,
    val borderInner: Float,
    val borderRadius: Float,
    val borderColor: Int,
    val layers: List<EditorLayer>,
    val activeLayerId: String?
)

enum class BrushStyle(val label: String) {
    STANDARD("Standard"),
    CHISEL("Evidenziatore"),
    NEON("Neon"),
    SPRAY("Spray"),
    GLITTER("Glitter"),
    ERASER("Gomma")
}

enum class WarpMode(val label: String) {
    PUSH("Spingi"),
    TWIRL_CW("Vortice +"),
    TWIRL_CCW("Vortice -"),
    PINCH("Comprimi"),
    BULGE("Gonfia"),
    RESET("Ripristina")
}

enum class CropSubTab {
    CROP, ROTATE, PERSPECTIVE
}

enum class CutoutMode(val label: String) {
    LASSO("Lazo Libero"),
    BRUSH("Pennello Sel."),
    SHAPE("Forma Ritaglio")
}

data class CollageSlot(
    val id: Int,
    val left: Float, // 0..1
    val top: Float,
    val right: Float,
    val bottom: Float,
    val bitmap: Bitmap? = null
)
