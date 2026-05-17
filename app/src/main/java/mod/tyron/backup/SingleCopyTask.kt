package mod.tyron.backup

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class SingleCopyTask(private val context: Context, private val callback: CallBackTask) {

    interface CallBackTask {
        fun onCopyPreExecute()
        fun onCopyProgressUpdate(progress: Int)
        fun onCopyPostExecute(path: String, success: Boolean, error: String)
    }

    fun copyFile(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            callback.onCopyPreExecute()
            val result = withContext(Dispatchers.IO) {
                copyFileInBackground(uri)
            }
            callback.onCopyPostExecute(result.first, result.second, result.third)
        }
    }

    @SuppressLint("Range")
    private suspend fun copyFileInBackground(uri: Uri): Triple<String, Boolean, String> {
        var outputPath = ""
        var errorReason = ""
        var success = false

        try {
            val folder = context.cacheDir
            val returnCursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open input stream")
            val size = returnCursor?.use {
                if (!it.moveToFirst()) {
                    -1L
                } else {
                    when (uri.scheme) {
                        "content" -> it.getLong(it.getColumnIndex(OpenableColumns.SIZE))
                        "file" -> File(uri.path ?: throw IOException("Invalid file Uri")).length()
                        else -> -1L
                    }
                }
            } ?: -1L

            if (size == 0L) throw IOException("Empty file (size = 0)")

            val sanitizedFileName = sanitizeFileName(getFileName(uri))
            val file = createUniqueTarget(folder, sanitizedFileName)
            outputPath = file.absolutePath

            inputStream.use { input ->
                BufferedInputStream(input).use { bis ->
                    FileOutputStream(file).use { fos ->
                        val data = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        var lastProgress = -1
                        while (true) {
                            val count = bis.read(data)
                            if (count == -1) break
                            total += count.toLong()
                            fos.write(data, 0, count)
                            if (size > 0L) {
                                val progress = ((total * 100L) / size).toInt().coerceIn(0, 100)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    withContext(Dispatchers.Main.immediate) {
                                        callback.onCopyProgressUpdate(progress)
                                    }
                                }
                            }
                        }
                        fos.flush()
                    }
                }
            }
            success = true
        } catch (e: IOException) {
            errorReason = e.message ?: "Unknown error"
        }

        return Triple(outputPath, success, errorReason)
    }

    @SuppressLint("Range")
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown"
    }

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.substringAfterLast('/').substringAfterLast('\\').trim()
        return trimmed.replace(Regex("[\r\n]"), "_").ifBlank { "unknown" }
    }

    private fun createUniqueTarget(folder: File, fileName: String): File {
        var candidate = File(folder, fileName)
        if (!candidate.exists()) {
            return candidate
        }
        val baseName = candidate.nameWithoutExtension.ifBlank { "file" }
        val extension = candidate.extension.takeIf { it.isNotBlank() }?.let { ".${it}" } ?: ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(folder, "$baseName ($index)$extension")
            index++
        }
        return candidate
    }
}
