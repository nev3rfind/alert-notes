package com.alertnotes.data.remote

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Avatar images in Firebase Storage, one object per account at
 * `avatars/{uid}.jpg`. Bytes arrive already cropped and compressed (the
 * feature layer owns the image pipeline); this class only moves them.
 */
@Singleton
class AvatarStorageDataSource @Inject constructor(
    private val storage: FirebaseStorage,
) {

    /**
     * Uploads the avatar, reporting [onProgress] with a 0..1 fraction, and
     * returns the public download URL.
     */
    suspend fun upload(
        uid: String,
        imageBytes: ByteArray,
        onProgress: (Float) -> Unit,
    ): String {
        val reference = avatarReference(uid)
        val metadata = StorageMetadata.Builder()
            .setContentType(CONTENT_TYPE_JPEG)
            .build()
        val task = reference.putBytes(imageBytes, metadata)
        task.addOnProgressListener { snapshot ->
            val total = snapshot.totalByteCount
            if (total > 0) {
                onProgress(snapshot.bytesTransferred.toFloat() / total.toFloat())
            }
        }
        task.await()
        return reference.downloadUrl.await().toString()
    }

    /** Removes the stored avatar; a never-uploaded avatar is not an error. */
    suspend fun delete(uid: String) {
        try {
            avatarReference(uid).delete().await()
        } catch (exception: StorageException) {
            if (exception.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                throw exception
            }
        }
    }

    private fun avatarReference(uid: String): StorageReference =
        storage.reference.child("$AVATARS_FOLDER/$uid.jpg")

    private companion object {
        const val AVATARS_FOLDER = "avatars"
        const val CONTENT_TYPE_JPEG = "image/jpeg"
    }
}
