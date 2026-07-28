package com.alertnotes.features.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The avatar image pipeline. [decodeForEditing] produces a bounded,
 * EXIF-upright bitmap for the interactive editor; [renderAvatar] replays the
 * editor's exact transform (rotate → zoom → pan inside a square viewport)
 * onto a [TARGET_SIZE_PX] JPEG, so what the user previews is what uploads.
 * Everything runs off the main thread.
 */
@Singleton
class AvatarImageProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Upright bitmap capped near [EDIT_MAX_SIZE_PX] on its smaller side. */
    suspend fun decodeForEditing(uri: Uri): Bitmap = withContext(Dispatchers.Default) {
        decode(uri)
    }

    /**
     * Renders the final avatar. The editor shows the image in a square
     * viewport of [viewportPx] with the same ordered transform used here —
     * centre the image, rotate by 90° × [rotationSteps], apply
     * cover-fit × [zoom], then pan by ([offsetX], [offsetY]) viewport px.
     */
    suspend fun renderAvatar(
        source: Bitmap,
        rotationSteps: Int,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        viewportPx: Float,
    ): ByteArray = withContext(Dispatchers.Default) {
        val output = Bitmap.createBitmap(TARGET_SIZE_PX, TARGET_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scaleToOutput = TARGET_SIZE_PX / viewportPx
        canvas.scale(scaleToOutput, scaleToOutput)
        canvas.translate(viewportPx / 2f + offsetX, viewportPx / 2f + offsetY)
        canvas.rotate(rotationSteps * 90f)
        val total = coverScale(source, viewportPx) * zoom
        canvas.scale(total, total)
        canvas.drawBitmap(
            source,
            -source.width / 2f,
            -source.height / 2f,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }
    }

    private fun decode(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder handles EXIF orientation itself.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val smallestSide = min(info.size.width, info.size.height)
                decoder.setTargetSampleSize(max(1, smallestSide / EDIT_MAX_SIZE_PX))
            }
        } else {
            decodeLegacy(uri)
        }

    /** API 26–27: BitmapFactory plus a manual EXIF rotation pass. */
    private fun decodeLegacy(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val smallestSide = max(1, min(bounds.outWidth, bounds.outHeight))
        var sample = 1
        while (smallestSide / (sample * 2) >= EDIT_MAX_SIZE_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Cannot decode $uri")
        val rotationDegrees = resolver.openInputStream(uri)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        return if (rotationDegrees == 0f) {
            bitmap
        } else {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }

    companion object {
        const val TARGET_SIZE_PX = 512
        const val JPEG_QUALITY = 85
        private const val EDIT_MAX_SIZE_PX = 1600

        /**
         * Scale that makes the image exactly cover the viewport. Rotation
         * by 90° steps only swaps width and height, so the binding smaller
         * side is the same at every step.
         */
        fun coverScale(source: Bitmap, viewportPx: Float): Float =
            viewportPx / min(source.width, source.height).toFloat()

        /** Largest pan (per axis) that keeps the image covering the viewport. */
        fun maxOffset(
            source: Bitmap,
            rotationSteps: Int,
            zoom: Float,
            viewportPx: Float,
        ): Pair<Float, Float> {
            val total = coverScale(source, viewportPx) * zoom
            val widthInViewport = if (rotationSteps % 2 == 0) source.width else source.height
            val heightInViewport = if (rotationSteps % 2 == 0) source.height else source.width
            val maxX = max(0f, (widthInViewport * total - viewportPx) / 2f)
            val maxY = max(0f, (heightInViewport * total - viewportPx) / 2f)
            return maxX to maxY
        }
    }
}
