package com.alertnotes.core.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where camera-proof acknowledgements live between capture and upload.
 * One file per reminder — the latest acknowledgement is the proof that
 * matters, and the sharing sweep uploads it before the next fire could
 * plausibly overwrite it. Files sit in app-private storage (no gallery,
 * no media store) and are shared with the camera app only through the
 * FileProvider grant for the duration of the capture.
 */
object AckProofStore {

    private const val DIRECTORY = "ack_proofs"
    private const val AUTHORITY = "com.alertnotes.fileprovider"

    fun fileFor(context: Context, reminderId: Long): File {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        return File(directory, "$reminderId.jpg")
    }

    /** Content URI the camera app writes the live capture into. */
    fun captureUriFor(context: Context, reminderId: Long): Uri =
        FileProvider.getUriForFile(context, AUTHORITY, fileFor(context, reminderId))
}
