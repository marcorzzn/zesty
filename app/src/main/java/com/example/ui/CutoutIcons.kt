package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icone disegnate per Scontorna: ognuna mostra il risultato dell'azione
 * (la persona davanti agli scacchi = sfondo tolto, la cornice = sfondo uniforme, ecc.).
 * Gli scacchi e le parti secondarie sono semitrasparenti, così l'icona resta leggibile con un solo colore.
 */
object CutIcons {
    private val ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.fill(alpha: Float = 1f, evenOdd: Boolean = false, p: PathBuilder.() -> Unit) =
        path(fill = ink, fillAlpha = alpha, pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = p)

    private fun ImageVector.Builder.line(width: Float = 2f, alpha: Float = 1f, p: PathBuilder.() -> Unit) =
        path(stroke = ink, strokeAlpha = alpha, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = p)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy); arcTo(r, r, 0f, true, true, cx + r, cy); arcTo(r, r, 0f, true, true, cx - r, cy); close()
    }

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) { moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close() }

    private fun PathBuilder.star(cx: Float, cy: Float, r: Float) {
        val k = r * 0.28f
        moveTo(cx, cy - r); lineTo(cx + k, cy - k); lineTo(cx + r, cy); lineTo(cx + k, cy + k)
        lineTo(cx, cy + r); lineTo(cx - k, cy + k); lineTo(cx - r, cy); lineTo(cx - k, cy - k); close()
    }

    /** Busto: testa e spalle. */
    private fun ImageVector.Builder.person(cx: Float = 12f, top: Float = 5f, s: Float = 1f, alpha: Float = 1f) {
        fill(alpha) { circle(cx, top + 3.6f * s, 3.6f * s) }
        fill(alpha) {
            val y = top + 9f * s
            moveTo(cx - 7.5f * s, 22f); curveTo(cx - 7.5f * s, y + 2.4f * s, cx - 4f * s, y, cx, y)
            curveTo(cx + 4f * s, y, cx + 7.5f * s, y + 2.4f * s, cx + 7.5f * s, 22f); close()
        }
    }

    private fun ImageVector.Builder.checker(alpha: Float = 0.38f) {
        fill(alpha) {
            for (y in 0 until 5) for (x in 0 until 5) if ((x + y) % 2 == 0) {
                val l = 2f + x * 4f; val t = 2f + y * 4f
                rect(l, t, l + 4f, t + 4f)
            }
        }
    }

    /** Sfondo automatico: la persona resta, dietro solo scacchi (trasparenza). */
    val RemoveBackground = icon("RemoveBackground") { checker(); person() }

    /** Soggetto automatico: persona riconosciuta, con la scintilla dell'automatico. */
    val AutoSubject = icon("AutoSubject") {
        checker(0.28f)
        person(cx = 10.5f, top = 6f, s = 0.92f)
        fill { star(19f, 5f, 3.6f) }
    }

    /** Seleziona: angoli di una selezione e il dito che tocca. */
    val Select = icon("Select") {
        line(2f) { moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f) }
        line(2f) { moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f) }
        line(2f) { moveTo(3f, 16f); lineTo(3f, 21f); lineTo(8f, 21f) }
        fill { moveTo(10f, 9f); lineTo(20f, 13.2f); lineTo(15.6f, 14.8f); lineTo(19.2f, 19.6f); lineTo(17.4f, 21f); lineTo(13.8f, 16.2f); lineTo(10.8f, 19.6f); close() }
    }

    /** Rifinisci: profilo di una testa con ciocche sottili. */
    val RefineHair = icon("RefineHair") {
        fill {
            moveTo(5f, 22f); lineTo(5f, 13f); curveTo(5f, 8.5f, 8.2f, 6f, 12f, 6f)
            curveTo(15.8f, 6f, 19f, 8.5f, 19f, 13f); lineTo(19f, 22f); close()
        }
        line(1.2f) { moveTo(8f, 7.6f); curveTo(6.5f, 5.4f, 5f, 4.6f, 3f, 4.6f) }
        line(1.2f) { moveTo(11f, 6.1f); curveTo(10.4f, 4f, 9.4f, 2.8f, 8f, 2f) }
        line(1.2f) { moveTo(14f, 6.2f); curveTo(15f, 4f, 16.4f, 3f, 18.5f, 2.6f) }
        line(1.2f) { moveTo(17f, 8f); curveTo(18.6f, 6.6f, 20f, 6.3f, 22f, 6.6f) }
    }

    /** Bacchetta: la punta tocca un'area e la seleziona tutta (area tratteggiata dal colore). */
    val Wand = icon("Wand") {
        fill(0.4f) {
            moveTo(2.5f, 6f); curveTo(2.5f, 3.5f, 5f, 2.2f, 7.5f, 2.6f); curveTo(10.5f, 3f, 12.6f, 5f, 12.4f, 7.6f)
            curveTo(12.2f, 10.6f, 9.6f, 12.6f, 6.6f, 12.4f); curveTo(4f, 12.2f, 2.5f, 9.2f, 2.5f, 6f); close()
        }
        line(2.6f) { moveTo(21f, 21f); lineTo(10.5f, 10.5f) }
        fill { star(9.2f, 9.2f, 3.4f) }
        fill { circle(17.5f, 6.5f, 1.2f) }
        fill { circle(6.5f, 17.5f, 1.2f) }
    }

    /** Lazo: anello libero con la coda. */
    val Lasso = icon("Lasso") {
        line(2f) {
            moveTo(7.6f, 13.6f); curveTo(4.4f, 12.8f, 2.8f, 10.8f, 3.2f, 8.6f); curveTo(3.8f, 5.2f, 8f, 3f, 12.6f, 3.2f)
            curveTo(17.4f, 3.4f, 21f, 5.8f, 20.8f, 8.8f); curveTo(20.6f, 12f, 16.4f, 14.2f, 11.6f, 14f)
        }
        line(2f) { moveTo(9.6f, 12.4f); curveTo(7.4f, 14.4f, 7.8f, 17f, 9.6f, 18.2f); curveTo(11f, 19.2f, 10.4f, 21f, 8.6f, 21.4f) }
    }

    /** Poligono: lati dritti e punti agli angoli. */
    val Polygon = icon("Polygon") {
        line(1.8f) { moveTo(4f, 17f); lineTo(7f, 5f); lineTo(17f, 3.5f); lineTo(20.5f, 13f); lineTo(12.5f, 20.5f); close() }
        fill { circle(4f, 17f, 2f); circle(7f, 5f, 2f); circle(17f, 3.5f, 2f); circle(20.5f, 13f, 2f); circle(12.5f, 20.5f, 2f) }
    }

    /** Sfondo uniforme: la cornice di colore pieno attorno al soggetto viene tolta. */
    val UniformBackground = icon("UniformBackground") {
        fill(0.42f, evenOdd = true) { rect(2f, 2f, 22f, 22f); rect(6f, 6f, 18f, 22f) }
        person(cx = 12f, top = 8f, s = 0.75f)
    }

    /** Anche i buchi interni: anello, lo sfondo si vede anche dentro. */
    val InnerHoles = icon("InnerHoles") {
        fill(evenOdd = true) { circle(12f, 12f, 9.5f); circle(12f, 12f, 4.2f) }
        fill(0.38f) { rect(9f, 9f, 12f, 12f); rect(12f, 12f, 15f, 15f) }
    }

    /** Contigua: si prende solo l'area collegata al tocco (quella separata resta fuori). */
    val Contiguous = icon("Contiguous") {
        fill {
            moveTo(2.5f, 13f); curveTo(2.5f, 8f, 6f, 5f, 10f, 5.5f); curveTo(14f, 6f, 15f, 10f, 14f, 13.5f)
            curveTo(13f, 17.5f, 9.5f, 19.5f, 6.5f, 18.5f); curveTo(4f, 17.6f, 2.5f, 15.6f, 2.5f, 13f); close()
        }
        line(1.6f, 0.55f) { circle(18.5f, 7f, 3f) }
        line(1.6f, 0.55f) { circle(19f, 17.5f, 2.2f) }
    }

    /** Segue le sfumature: tre bande che si schiariscono. */
    val Gradual = icon("Gradual") {
        fill(1f) { rect(2f, 4f, 8f, 20f) }
        fill(0.62f) { rect(9f, 4f, 15f, 20f) }
        fill(0.3f) { rect(16f, 4f, 22f, 20f) }
    }

    /** Togli alone: persona con l'alone attorno barrato. */
    val Dehalo = icon("Dehalo") {
        line(2.2f, 0.45f) { circle(12f, 12f, 10f) }
        person(cx = 12f, top = 6f, s = 0.72f)
        line(2f) { moveTo(4.5f, 4.5f); lineTo(19.5f, 19.5f) }
    }

    /** Togli isolotti: il pezzo grande resta, i puntini sparsi sono vuoti. */
    val Islands = icon("Islands") {
        fill { circle(10f, 13f, 7f) }
        line(1.4f, 0.6f) { circle(20f, 4.5f, 1.8f) }
        line(1.4f, 0.6f) { circle(20.5f, 13f, 1.4f) }
        line(1.4f, 0.6f) { circle(4f, 3.5f, 1.4f) }
    }

    /** Solo soggetto principale: il pezzo grande dentro il mirino. */
    val MainSubject = icon("MainSubject") {
        line(2f) { moveTo(2.5f, 7.5f); lineTo(2.5f, 2.5f); lineTo(7.5f, 2.5f) }
        line(2f) { moveTo(16.5f, 2.5f); lineTo(21.5f, 2.5f); lineTo(21.5f, 7.5f) }
        line(2f) { moveTo(2.5f, 16.5f); lineTo(2.5f, 21.5f); lineTo(7.5f, 21.5f) }
        line(2f) { moveTo(16.5f, 21.5f); lineTo(21.5f, 21.5f); lineTo(21.5f, 16.5f) }
        person(cx = 12f, top = 6f, s = 0.7f)
    }

    /** Riempi buchi: forma piena con il buco richiuso. */
    val FillHoles = icon("FillHoles") {
        fill(0.38f) { circle(12f, 12f, 9.5f) }
        fill { circle(12f, 12f, 4.6f) }
        line(1.8f) { circle(12f, 12f, 9.5f) }
    }

    /** Espandi / contrai: frecce verso l'esterno e verso l'interno del bordo. */
    val GrowShrink = icon("GrowShrink") {
        line(1.6f, 0.6f) { circle(12f, 12f, 5.5f) }
        line(2f) { moveTo(12f, 1.5f); lineTo(12f, 5f); moveTo(10f, 3.5f); lineTo(12f, 1.5f); lineTo(14f, 3.5f) }
        line(2f) { moveTo(12f, 22.5f); lineTo(12f, 19f); moveTo(10f, 20.5f); lineTo(12f, 22.5f); lineTo(14f, 20.5f) }
        line(2f) { moveTo(1.5f, 12f); lineTo(5f, 12f); moveTo(3.5f, 10f); lineTo(1.5f, 12f); lineTo(3.5f, 14f) }
        line(2f) { moveTo(22.5f, 12f); lineTo(19f, 12f); moveTo(20.5f, 10f); lineTo(22.5f, 12f); lineTo(20.5f, 14f) }
    }

    /** Sfuma: bordo che si dissolve in puntini. */
    val Feather = icon("Feather") {
        fill { circle(9f, 12f, 6f) }
        fill(0.7f) { circle(16.5f, 8f, 1.6f); circle(17f, 12f, 1.6f); circle(16.5f, 16f, 1.6f) }
        fill(0.4f) { circle(20.5f, 10f, 1.1f); circle(20.5f, 14f, 1.1f) }
    }

    /** Ritaglia al soggetto: angoli di ritaglio stretti attorno alla persona. */
    val TrimToSubject = icon("TrimToSubject") {
        line(2f) { moveTo(5f, 1.5f); lineTo(5f, 19f); lineTo(22.5f, 19f) }
        line(2f) { moveTo(1.5f, 5f); lineTo(19f, 5f); lineTo(19f, 22.5f) }
        person(cx = 12f, top = 7f, s = 0.58f)
    }

    /** Modalità togli: il pezzo diventa trasparente (scacchi), con il meno ritagliato nel bollo. */
    val ModeRemove = icon("ModeRemove") {
        checker(0.4f)
        fill(evenOdd = true) { circle(15.5f, 15.5f, 6.8f); rect(12.2f, 14.4f, 18.8f, 16.6f) }
    }

    /** Modalità rimetti: il pezzo torna pieno, con il più ritagliato nel bollo. */
    val ModeRestore = icon("ModeRestore") {
        fill(0.55f) { rect(2f, 2f, 14f, 14f) }
        fill(evenOdd = true) {
            circle(15.5f, 15.5f, 6.8f)
            moveTo(14.4f, 12.2f); lineTo(16.6f, 12.2f); lineTo(16.6f, 14.4f); lineTo(18.8f, 14.4f); lineTo(18.8f, 16.6f)
            lineTo(16.6f, 16.6f); lineTo(16.6f, 18.8f); lineTo(14.4f, 18.8f); lineTo(14.4f, 16.6f); lineTo(12.2f, 16.6f)
            lineTo(12.2f, 14.4f); lineTo(14.4f, 14.4f); close()
        }
    }
}
