package com.spcollector.gacha.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class MlKitScanner(private val ctx: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(uri: Uri): List<OcrLine> {
        val text = recognizer.process(InputImage.fromFilePath(ctx, uri)).await()
        return text.textBlocks.flatMap { it.lines }.map { OcrLine(it.text, it.confidence) }
    }

    fun sha256(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        ctx.contentResolver.openInputStream(uri)!!.use { s ->
            val buf = ByteArray(8192)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
