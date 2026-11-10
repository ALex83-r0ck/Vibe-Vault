package com.alexander.vibevault.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.alexander.vibevault.domain.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Texterkennung auf dem Gerät (ML Kit, offline). Fotos verlassen das Gerät nicht. */
class LabelScanner(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val scanDir get() = File(context.cacheDir, "scans").apply { mkdirs() }

    /** Ziel-URI für ein neues Kamerafoto. Reste früherer (abgebrochener) Aufnahmen werden vorher entfernt. */
    fun createCaptureUri(): Uri {
        val dir = scanDir
        dir.listFiles()?.forEach { it.delete() }
        val file = File.createTempFile("label_", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun readLines(uri: Uri): List<OcrLine> {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    cont.resume(text.textBlocks.flatMap { block ->
                        block.lines.map { OcrLine(it.text, it.boundingBox?.height() ?: 0) }
                    })
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /** Eigene Kamerafotos wieder löschen. */
    suspend fun clearCapturedPhotos() = withContext(Dispatchers.IO) {
        scanDir.listFiles()?.forEach { it.delete() }
    }
}
