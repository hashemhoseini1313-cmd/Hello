package com.modeler3d.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtil {

    fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("فایل انتخاب‌شده تصویر معتبر نیست")
        }

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("خواندن تصویر ناموفق بود")

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        val longestNow = maxOf(decoded.width, decoded.height)
        if (longestNow > maxSide) {
            val s = maxSide.toFloat() / longestNow
            matrix.postScale(s, s)
        }

        val transformed = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true
        )

        // پس‌زمینه سفید، تا عکس‌های PNG شفاف سیاه نشوند
        val out = Bitmap.createBitmap(
            transformed.width, transformed.height, Bitmap.Config.ARGB_8888
        )
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(transformed, 0f, 0f, null)
        }
        return out
    }

    fun prepareJpeg(context: Context, uri: Uri): ByteArray {
        val bmp = loadBitmap(context, uri, 2048)
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    fun thumbnail(context: Context, uri: Uri): Bitmap {
        return loadBitmap(context, uri, 512)
    }
}
