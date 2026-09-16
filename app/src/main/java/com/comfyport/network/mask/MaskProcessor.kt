package com.comfyport.network.mask

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.comfyport.network.AppLogger
import com.comfyport.network.PngEncoder
import java.io.ByteArrayInputStream

/**
 * Handles inpainting mask preparation and image composting for ComfyUI.
 * Decoupled from ComfyClient.
 */
object MaskProcessor {

    /**
     * Composes the inpainting mask into the image's Alpha channel for ComfyUI's standard LoadImage node.
     * In ComfyUI, LoadImage extracts its MASK output from the alpha channel via: mask = 1.0 - (alpha / 255.0).
     * Therefore:
     * - Pixels to inpaint (masked) must have Alpha = 0 (so 1.0 - 0.0 = 1.0).
     * - Preserved pixels (unmasked) must have Alpha = 255 (so 1.0 - 1.0 = 0.0).
     * Also normalizes EXIF camera rotation so image and mask are perfectly aligned.
     */
    fun createCompositeImageWithMask(imageBytes: ByteArray, maskBytes: ByteArray): ByteArray {
        return try {
            val baseImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return imageBytes

            var orientedImage = baseImage
            try {
                val exifInterface = android.media.ExifInterface(ByteArrayInputStream(imageBytes))
                val orientation = exifInterface.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                }
                if (!matrix.isIdentity) {
                    val transformed = Bitmap.createBitmap(baseImage, 0, 0, baseImage.width, baseImage.height, matrix, true)
                    if (transformed != baseImage) {
                        orientedImage = transformed
                    }
                }
            } catch (_: Throwable) {}

            val rawMask = BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size)
                ?: return imageBytes

            val w = orientedImage.width
            val h = orientedImage.height
            val scaledMask = if (rawMask.width != w || rawMask.height != h) {
                Bitmap.createScaledBitmap(rawMask, w, h, true)
            } else rawMask

            val imgPixels = IntArray(w * h)
            val maskPixels = IntArray(w * h)
            orientedImage.getPixels(imgPixels, 0, w, 0, 0, w, h)
            scaledMask.getPixels(maskPixels, 0, w, 0, 0, w, h)

            val rgbaBytes = ByteArray(w * h * 4)
            var offset = 0
            for (i in 0 until w * h) {
                val imgPixel = imgPixels[i]
                val maskPixel = maskPixels[i]

                val r = (imgPixel ushr 16) and 0xFF
                val g = (imgPixel ushr 8) and 0xFF
                val b = imgPixel and 0xFF

                val maskA = (maskPixel ushr 24) and 0xFF
                val maskR = (maskPixel ushr 16) and 0xFF
                val maskG = (maskPixel ushr 8) and 0xFF
                val maskB = maskPixel and 0xFF

                val lum = (maskR * 299 + maskG * 587 + maskB * 114) / 1000
                val maskIntensity = if (maskA == 0) 0 else ((lum * maskA) / 255).coerceIn(0, 255)

                val outAlpha = 255 - maskIntensity

                // Crucial: preserve 100% original RGB colors even when outAlpha = 0 (un-premultiplied)
                rgbaBytes[offset] = r.toByte()
                rgbaBytes[offset + 1] = g.toByte()
                rgbaBytes[offset + 2] = b.toByte()
                rgbaBytes[offset + 3] = outAlpha.toByte()
                offset += 4
            }

            PngEncoder.encodeRgba(w, h, rgbaBytes)
        } catch (e: Throwable) {
            AppLogger.e("MaskProcessor", "Failed to create composite image with mask: ${e.message}")
            imageBytes
        }
    }

    /**
     * Generates a standalone mask compatible with all channel modes of LoadImageMask (red, green, blue, alpha).
     * For masked pixels: RGBA(255, 255, 255, 0)
     * For unmasked pixels: RGBA(0, 0, 0, 255)
     * Preserves un-premultiplied RGB and smooth feathered intensity gradients.
     */
    fun createStandaloneMaskImage(maskBytes: ByteArray): ByteArray {
        return try {
            val rawMask = BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size)
                ?: return maskBytes
            val w = rawMask.width
            val h = rawMask.height
            val pixels = IntArray(w * h)
            rawMask.getPixels(pixels, 0, w, 0, 0, w, h)

            val rgbaBytes = ByteArray(w * h * 4)
            var offset = 0
            for (i in 0 until w * h) {
                val p = pixels[i]
                val a = (p ushr 24) and 0xFF
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF

                val lum = (r * 299 + g * 587 + b * 114) / 1000
                val maskIntensity = if (a == 0) 0 else ((lum * a) / 255).coerceIn(0, 255)

                rgbaBytes[offset] = maskIntensity.toByte()
                rgbaBytes[offset + 1] = maskIntensity.toByte()
                rgbaBytes[offset + 2] = maskIntensity.toByte()
                rgbaBytes[offset + 3] = (255 - maskIntensity).toByte()
                offset += 4
            }
            PngEncoder.encodeRgba(w, h, rgbaBytes)
        } catch (e: Throwable) {
            AppLogger.e("MaskProcessor", "Failed to create standalone mask image: ${e.message}")
            maskBytes
        }
    }
}
