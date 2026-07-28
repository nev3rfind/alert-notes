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

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * One location-proof outcome captured at acknowledgement time. When
     * acquisition failed and the user chose to close the reminder anyway,
     * [available] is false and the failure details travel to the sender
     * instead of coordinates — tracking must never fake a success.
     */
    @kotlinx.serialization.Serializable
    data class LocationProof(
        val available: Boolean = true,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val accuracyMeters: Double = 0.0,
        val altitudeMeters: Double? = null,
        val speedMps: Double? = null,
        val address: String = "",
        val city: String = "",
        val region: String = "",
        val country: String = "",
        val failureReason: String = "",
        val attemptSeconds: Long = 0,
        val capturedAtMillis: Long,
    )

    fun fileFor(context: Context, reminderId: Long): File {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        return File(directory, "$reminderId.jpg")
    }

    /** Content URI the camera app writes the live capture into. */
    fun captureUriFor(context: Context, reminderId: Long): Uri =
        FileProvider.getUriForFile(context, AUTHORITY, fileFor(context, reminderId))

    private fun locationFileFor(context: Context, reminderId: Long): File {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        return File(directory, "$reminderId.location.json")
    }

    fun writeLocationProof(context: Context, reminderId: Long, proof: LocationProof) {
        locationFileFor(context, reminderId)
            .writeText(json.encodeToString(LocationProof.serializer(), proof))
    }

    /** Reads and REMOVES the proof — it is consumed by exactly one mirror. */
    fun consumeLocationProof(context: Context, reminderId: Long): LocationProof? {
        val file = locationFileFor(context, reminderId)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(LocationProof.serializer(), file.readText())
        }.getOrNull().also { file.delete() }
    }
}
