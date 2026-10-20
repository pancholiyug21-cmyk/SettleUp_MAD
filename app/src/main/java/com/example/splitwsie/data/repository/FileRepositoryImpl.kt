package com.example.splitwsie.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.splitwsie.domain.repository.FileRepository
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

class FileRepositoryImpl @Inject constructor(
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context
) : FileRepository {

    override suspend fun uploadExpenseReceipt(
        groupId: String?,
        expenseId: String,
        uriString: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)

            // 1. Compress image
            val compressedBytes = compressImage(uri)
                ?: return@withContext Result.failure(Exception("Failed to read or compress image."))

            // 2. Upload to Firebase Storage
            val ext = "jpg" // Always compressing to JPEG
            val fileId = UUID.randomUUID().toString()
            val safeGroupId = groupId ?: "no-group"
            val path = "receipts/$safeGroupId/$expenseId/$fileId.$ext"
            val ref = storage.reference.child(path)

            ref.putBytes(compressedBytes).await()
            val downloadUrl = ref.downloadUrl.await()

            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteExpenseReceipt(receiptUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (receiptUrl.isNotBlank()) {
                val ref = storage.getReferenceFromUrl(receiptUrl)
                ref.delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // If it fails to delete (e.g. already deleted or permission denied), we don't necessarily want to block the user.
            Result.failure(e)
        }
    }

    private fun compressImage(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // Calculate inSampleSize to resize image to max 1920x1080
                options.inSampleSize = calculateInSampleSize(options, 1920, 1080)
                options.inJustDecodeBounds = false

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    val baos = ByteArrayOutputStream()
                    // Compress to JPEG with 80% quality
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    bitmap?.recycle()
                    baos.toByteArray()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
