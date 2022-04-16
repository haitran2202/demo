package com.example.demo

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.*

object FileUtils {

    fun getUriFromStringShare(context: Context, path: String): Uri? {
        return if (path.startsWith("content://")) {
            Uri.parse(path)
        } else {
            val newPath = copyCacheFileToPublicFile(context, File(path))
            if (newPath.startsWith("content://")) {
                Uri.parse(newPath)
            } else {
                val values = ContentValues(2)
                values.put(
                    MediaStore.Images.Media.MIME_TYPE,
                    getMimeType(Uri.encode(newPath))
                )
                values.put(MediaStore.Images.Media.DATA, newPath)
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )
            }

        }
    }

    fun getMimeType(url: String?): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
    }

    fun copyToCacheFile(context: Context, path: String): File? {
        return copyToNewFile(context, Uri.parse(path))
    }

    fun copyToNewFile(context: Context, uri: Uri): File? {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r", null)
            val fd = pfd?.fileDescriptor
            val inputStream = FileInputStream(fd)
            val byteArr = readBinaryStream(inputStream, pfd?.statSize?.toInt() ?: 0)
            val cacheFile = File(context.cacheDir, getFileName(context, uri))
            writeFile(cacheFile, byteArr)
            inputStream.close()
            return cacheFile
        } catch (e: Exception) {
            File(uri.path ?: "")
        }
    }

    @SuppressLint("Range")
    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
        }
        cursor?.close()

        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    private fun readBinaryStream(
        stream: InputStream,
        byteCount: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(if (byteCount > 0) byteCount else 4096)
            var read: Int
            while (stream.read(buffer).also { read = it } >= 0) {
                output.write(buffer, 0, read)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                stream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return output.toByteArray()
    }

    private fun writeFile(file: File, data: ByteArray): Boolean {
        return try {
            var output: BufferedOutputStream? = null
            try {
                output = BufferedOutputStream(FileOutputStream(file))
                output.write(data)
                output.flush()
                true
            } finally {
                output?.close()
            }
        } catch (ex: Exception) {
            false
        }
    }

    fun createFileFromName(context: Context, name: String?): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            values.put(
                MediaStore.MediaColumns.MIME_TYPE,
                getMimeType(Uri.encode(name))
            )
            values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + "Demo"
            )
            val uri =
                context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            uri.toString()
        } else {
            val folderDownload =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (folderDownload != null && !folderDownload.exists()) {
                folderDownload.mkdir()
            }
            val folder = File(folderDownload, "Demo")
            folder.mkdirs()
            File(folder, name).absolutePath
        }
    }

    private fun copyCacheFileToPublicFile(context: Context, file: File): String {
        val newPath = createFileFromName(context, file.name)
        var outputStream: OutputStream? = null
        if (newPath != null) {
            outputStream = if (newPath.startsWith("content://")) {
                context.contentResolver.openOutputStream(Uri.parse(newPath))
            } else {
                File(newPath).outputStream()
            }
        }
        val inputStream = FileInputStream(file)
        val buffer = ByteArray(4 * 1024) // or other buffer size

        var read: Int

        while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream?.write(buffer, 0, read)
        }
        outputStream?.flush()

        inputStream.close()
        return newPath!!
    }

}