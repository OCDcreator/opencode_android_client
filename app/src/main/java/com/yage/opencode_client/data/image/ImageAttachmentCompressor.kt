package com.yage.opencode_client.data.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageAttachmentCompressor @Inject constructor() {
    data class CompressedImage(
        val dataUri: String,
        val filename: String?,
        val byteSize: Int,
        val thumbnail: Bitmap
    )

    suspend fun compress(contentResolver: ContentResolver, uri: Uri): CompressedImage =
        withContext(Dispatchers.IO) {
            val filename = queryFilename(contentResolver, uri)

            // Step 1: Decode bounds
            val bounds = decodeBounds(contentResolver, uri)

            // Step 2: Read EXIF orientation (separate stream)
            val orientation = readExifOrientation(contentResolver, uri)

            // Step 3: Calculate sample size
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            val sampleSize = if (longEdge > TARGET_LONG_EDGE) {
                Integer.highestOneBit(longEdge / TARGET_LONG_EDGE).coerceAtLeast(1)
            } else 1

            // Step 4: Decode sampled bitmap (separate stream)
            val sampled = decodeSampled(contentResolver, uri, sampleSize)
                ?: throw IllegalStateException("Failed to decode image")

            // Step 5: Apply EXIF rotation
            val rotated = applyRotation(sampled, orientation)
            if (rotated !== sampled) sampled.recycle()

            // Step 6: Scale to exact target
            val scaled = scaleToTarget(rotated)
            if (scaled !== rotated) rotated.recycle()

            // Step 7: Flatten alpha to white (transparent PNG -> JPEG)
            val flattened = flattenAlpha(scaled)
            if (flattened !== scaled) scaled.recycle()

            // Step 8: Compress to JPEG
            val jpegBytes = compressJpeg(flattened, JPEG_QUALITY)

            // Step 9: Build thumbnail before recycling (guard: thumbnail may be same object for small images)
            val thumbnail = createThumbnail(flattened)
            if (thumbnail !== flattened) flattened.recycle()

            // Step 10: Base64 encode
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            ensureActive()

            val dataUri = "data:image/jpeg;base64,$base64"

            CompressedImage(
                dataUri = dataUri,
                filename = filename,
                byteSize = base64.length,
                thumbnail = thumbnail
            )
        }

    private fun queryFilename(resolver: ContentResolver, uri: Uri): String? {
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    private fun decodeBounds(resolver: ContentResolver, uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return options
    }

    private fun readExifOrientation(resolver: ContentResolver, uri: Uri): Int {
        return try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun decodeSampled(resolver: ContentResolver, uri: Uri, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToTarget(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= TARGET_LONG_EDGE) return bitmap
        val scale = TARGET_LONG_EDGE.toFloat() / longEdge.toFloat()
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun flattenAlpha(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return output
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun createThumbnail(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= THUMBNAIL_SIZE) return bitmap
        val scale = THUMBNAIL_SIZE.toFloat() / longEdge.toFloat()
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    companion object {
        private const val TARGET_LONG_EDGE = 1024
        private const val JPEG_QUALITY = 75
        private const val THUMBNAIL_SIZE = 128
    }
}
