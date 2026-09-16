package com.comfyport.network

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * High-performance, pure-Kotlin PNG encoder that preserves exact un-premultiplied RGBA pixel values.
 * Standard Android [android.graphics.Bitmap.compress] premultiplies RGB by Alpha, causing any pixel
 * with Alpha = 0 to be permanently zeroed out to black (0, 0, 0).
 *
 * This encoder preserves 100% of the original RGB colors under the mask so inpainting workflows
 * (such as SDXL and Flux inpainting with LoadImage) receive the original image context under the mask.
 */
object PngEncoder {

    fun encodeRgba(width: Int, height: Int, rgbaBytes: ByteArray): ByteArray {
        require(width > 0 && height > 0) { "Width and height must be positive" }
        val expectedBytes = width * height * 4
        require(rgbaBytes.size >= expectedBytes) {
            "rgbaBytes buffer too small: expected at least $expectedBytes bytes, got ${rgbaBytes.size}"
        }

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1. PNG Header (8 bytes: 137, 80, 78, 71, 13, 10, 26, 10)
        dos.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))

        // 2. IHDR Chunk (13 bytes)
        val ihdrBaos = ByteArrayOutputStream()
        val ihdrDos = DataOutputStream(ihdrBaos)
        ihdrDos.writeInt(width)
        ihdrDos.writeInt(height)
        ihdrDos.writeByte(8) // Bit depth: 8
        ihdrDos.writeByte(6) // Color type: 6 (RGBA)
        ihdrDos.writeByte(0) // Compression method: 0 (deflate)
        ihdrDos.writeByte(0) // Filter method: 0
        ihdrDos.writeByte(0) // Interlace method: 0
        writeChunk(dos, "IHDR", ihdrBaos.toByteArray())

        // 3. IDAT Chunk: Raw scanlines with filter byte 0 (None)
        val rowBytes = width * 4
        val rawScanlines = ByteArray(height * (1 + rowBytes))
        var srcPos = 0
        var dstPos = 0
        for (y in 0 until height) {
            rawScanlines[dstPos++] = 0 // Filter type: None
            System.arraycopy(rgbaBytes, srcPos, rawScanlines, dstPos, rowBytes)
            srcPos += rowBytes
            dstPos += rowBytes
        }

        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(rawScanlines)
        deflater.finish()

        val idatBaos = ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            idatBaos.write(buffer, 0, count)
        }
        deflater.end()

        writeChunk(dos, "IDAT", idatBaos.toByteArray())

        // 4. IEND Chunk
        writeChunk(dos, "IEND", ByteArray(0))

        dos.flush()
        return baos.toByteArray()
    }

    private fun writeChunk(dos: DataOutputStream, type: String, data: ByteArray) {
        dos.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        dos.write(typeBytes)
        dos.write(data)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        dos.writeInt(crc.value.toInt())
    }
}
