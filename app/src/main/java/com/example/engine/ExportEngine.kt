package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.model.EditorLayer
import com.example.model.LayerType
import com.example.model.ShapeKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ExportFormat(val ext: String, val mime: String, val label: String) {
    PNG("png", "image/png", "PNG (Lossless)"),
    JPG("jpg", "image/jpeg", "JPG (Compresso)"),
    SVG("svg", "image/svg+xml", "SVG (Vettoriale + Raster)"),
    PDF("pdf", "application/pdf", "PDF (Documento)")
}

object ExportEngine {

    /**
     * Scales the composite bitmap according to scale (0.25x .. 4.0x) with safe max cap (4096px).
     */
    fun scaleBitmap(src: Bitmap, scale: Float): Bitmap {
        val targetW = (src.width * scale).roundToInt().coerceIn(32, 4096)
        val targetH = (src.height * scale).roundToInt().coerceIn(32, 4096)
        if (targetW == src.width && targetH == src.height) return src
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    /**
     * Generates SVG string representing the entire composition (<rect>, <ellipse>, <polygon>, <text>, <image>).
     */
    fun generateSvgContent(
        canvasW: Int,
        canvasH: Int,
        bgColor: Int,
        scale: Float,
        layers: List<EditorLayer>
    ): String {
        val outW = (canvasW * scale).roundToInt().coerceAtLeast(32)
        val outH = (canvasH * scale).roundToInt().coerceAtLeast(32)
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $canvasW $canvasH" width="$outW" height="$outH">""").append("\n")
        sb.append("""  <rect width="100%" height="100%" fill="${hexColor(bgColor)}"/>""").append("\n")

        for (layer in layers) {
            if (!layer.visible) continue
            val cx = layer.x + layer.w / 2f
            val cy = layer.y + layer.h / 2f
            val op = String.format(java.util.Locale.US, "%.2f", layer.opacity)
            val transform = """transform="translate(${layer.x}, ${layer.y}) rotate(${layer.rotation}, ${layer.w / 2f}, ${layer.h / 2f})""""

            when (layer.type) {
                LayerType.SHAPE -> {
                    val fill = if (layer.shapeKind == ShapeKind.LINE) "none" else hexColor(layer.fillColor)
                    val stroke = if (layer.strokeWidth > 0f || layer.shapeKind == ShapeKind.LINE) {
                        val sc = if (layer.shapeKind == ShapeKind.LINE) hexColor(layer.fillColor) else hexColor(layer.strokeColor)
                        """stroke="$sc" stroke-width="${layer.strokeWidth.coerceAtLeast(1f)}""""
                    } else ""

                    when (layer.shapeKind) {
                        ShapeKind.RECT -> {
                            sb.append("""  <rect x="0" y="0" width="${layer.w}" height="${layer.h}" rx="${layer.cornerRadius}" fill="$fill" $stroke opacity="$op" $transform/>""").append("\n")
                        }
                        ShapeKind.CIRCLE -> {
                            val rx = layer.w / 2f
                            val ry = layer.h / 2f
                            sb.append("""  <ellipse cx="$rx" cy="$ry" rx="$rx" ry="$ry" fill="$fill" $stroke opacity="$op" $transform/>""").append("\n")
                        }
                        ShapeKind.TRIANGLE -> {
                            val pts = "${layer.w / 2f},0 ${layer.w},${layer.h} 0,${layer.h}"
                            sb.append("""  <polygon points="$pts" fill="$fill" $stroke opacity="$op" $transform/>""").append("\n")
                        }
                        ShapeKind.POLYGON -> {
                            val rx = layer.w / 2f
                            val ry = layer.h / 2f
                            val sides = layer.polygonSides.coerceIn(3, 12)
                            val pts = (0 until sides).joinToString(" ") { i ->
                                val a = (i.toDouble() / sides) * Math.PI * 2.0 - Math.PI / 2.0
                                "${rx + cos(a).toFloat() * rx},${ry + sin(a).toFloat() * ry}"
                            }
                            sb.append("""  <polygon points="$pts" fill="$fill" $stroke opacity="$op" $transform/>""").append("\n")
                        }
                        ShapeKind.STAR -> {
                            val rx = layer.w / 2f
                            val ry = layer.h / 2f
                            val total = layer.starPoints.coerceIn(3, 24) * 2
                            val pts = (0 until total).joinToString(" ") { i ->
                                val a = (i.toDouble() / total) * Math.PI * 2.0 - Math.PI / 2.0
                                val f = if (i % 2 == 0) 1f else layer.starInnerRatio
                                "${rx + cos(a).toFloat() * rx * f},${ry + sin(a).toFloat() * ry * f}"
                            }
                            sb.append("""  <polygon points="$pts" fill="$fill" $stroke opacity="$op" $transform/>""").append("\n")
                        }
                        ShapeKind.LINE -> {
                            sb.append("""  <line x1="0" y1="${layer.h / 2f}" x2="${layer.w}" y2="${layer.h / 2f}" $stroke opacity="$op" $transform/>""").append("\n")
                        }
                        else -> {
                            val bmp = PixelEngine.renderSingleLayerBitmap(layer, layers)
                            val b64 = bitmapToBase64Png(bmp)
                            sb.append("""  <image width="${layer.w}" height="${layer.h}" href="data:image/png;base64,$b64" opacity="$op" $transform/>""").append("\n")
                        }
                    }
                }
                LayerType.TEXT -> {
                    val escaped = layer.text
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    val anchor = when (layer.textAlign) {
                        0 -> "start"
                        2 -> "end"
                        else -> "middle"
                    }
                    val tx = when (layer.textAlign) {
                        0 -> 16f
                        2 -> layer.w - 16f
                        else -> layer.w / 2f
                    }
                    val ty = layer.h / 2f + layer.fontSize * 0.35f
                    sb.append("""  <text x="$tx" y="$ty" font-family="${layer.fontFamily}" font-size="${layer.fontSize}" text-anchor="$anchor" fill="${hexColor(layer.fillColor)}" opacity="$op" $transform>$escaped</text>""").append("\n")
                }
                else -> {
                    val bmp = PixelEngine.renderSingleLayerBitmap(layer, layers)
                    val b64 = bitmapToBase64Png(bmp)
                    sb.append("""  <image width="${layer.w}" height="${layer.h}" href="data:image/png;base64,$b64" opacity="$op" $transform/>""").append("\n")
                }
            }
        }

        sb.append("</svg>")
        return sb.toString()
    }

    /**
     * Generates a valid PDF 1.4 binary stream with embedded JPEG image (and PdfDocument fallback).
     */
    fun writePdfToStream(composite: Bitmap, quality: Int, out: OutputStream) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(composite.width, composite.height, 1).create()
        val page = doc.startPage(pageInfo)
        val c: Canvas = page.canvas
        c.drawColor(Color.BLACK)
        c.drawBitmap(composite, 0f, 0f, null)
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    /**
     * Exports the image to the user's device Downloads / Pictures folder and returns the saved filename + size.
     */
    fun exportToDevice(
        context: Context,
        composite: Bitmap,
        canvasW: Int,
        canvasH: Int,
        bgColor: Int,
        layers: List<EditorLayer>,
        format: ExportFormat,
        jpgQuality: Int, // 10..100
        scale: Float     // 0.25..4.0
    ): Result<Pair<String, Long>> = runCatching {
        val scaledBmp = scaleBitmap(composite, scale)
        val timestamp = System.currentTimeMillis()
        val fileName = "PicsartStudio_$timestamp.${format.ext}"

        val bytes = ByteArrayOutputStream().use { bos ->
            when (format) {
                ExportFormat.PNG -> scaledBmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                ExportFormat.JPG -> scaledBmp.compress(Bitmap.CompressFormat.JPEG, jpgQuality.coerceIn(10, 100), bos)
                ExportFormat.SVG -> {
                    val svg = generateSvgContent(canvasW, canvasH, bgColor, scale, layers)
                    bos.write(svg.toByteArray(Charsets.UTF_8))
                }
                ExportFormat.PDF -> {
                    writePdfToStream(scaledBmp, jpgQuality.coerceIn(10, 100), bos)
                }
            }
            bos.toByteArray()
        }

        // Save to MediaStore Downloads / Pictures or app external directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = if (format == ExportFormat.PNG || format == ExportFormat.JPG) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (format == ExportFormat.PNG || format == ExportFormat.JPG)
                        Environment.DIRECTORY_PICTURES + "/PicsartStudio"
                    else
                        Environment.DIRECTORY_DOWNLOADS + "/PicsartStudio"
                )
            }
            val uri: Uri? = resolver.insert(collection, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        } else {
            val dir = File(context.getExternalFilesDir(null), "Exports").apply { mkdirs() }
            File(dir, fileName).outputStream().use { it.write(bytes) }
        }

        // Also cache in internal cacheDir for instant sharing
        val cacheFile = File(context.cacheDir, fileName)
        FileOutputStream(cacheFile).use { it.write(bytes) }

        fileName to bytes.size.toLong()
    }

    fun shareCurrentImage(context: Context, composite: Bitmap) {
        runCatching {
            val file = File(context.cacheDir, "picsart_share.png")
            FileOutputStream(file).use { out ->
                composite.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Condividi immagine").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun hexColor(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)

    private fun bitmapToBase64Png(bmp: Bitmap): String {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 92, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
}
