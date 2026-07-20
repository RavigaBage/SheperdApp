package com.example.data.file

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.domain.model.ShepherdFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class SafFileManager(private val context: Context) {

    fun persistUriPermission(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun listFilesRecursive(folderUri: Uri): List<ShepherdFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<ShepherdFile>()
        val rootDoc = DocumentFile.fromTreeUri(context, folderUri)
        if (rootDoc != null && rootDoc.isDirectory) {
            scanDirectory(rootDoc, "", files)
        }
        files
    }

    private fun scanDirectory(dir: DocumentFile, currentPath: String, outList: MutableList<ShepherdFile>) {
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                val newPath = if (currentPath.isEmpty()) child.name.orEmpty() else "$currentPath/${child.name}"
                scanDirectory(child, newPath, outList)
            } else if (child.isFile) {
                val name = child.name.orEmpty()
                val ext = name.substringAfterLast(".", "").lowercase(Locale.ROOT)
                if (ext in listOf("docx", "doc", "pptx", "pdf", "txt", "mp3", "wav", "m4a")) {
                    outList.add(
                        ShepherdFile(
                            id = child.uri.toString(),
                            name = name,
                            extension = ext,
                            uriString = child.uri.toString(),
                            categoryId = null, // Linked via Room association inside database
                            parentPath = currentPath,
                            sizeBytes = child.length(),
                            lastModified = child.lastModified()
                        )
                    )
                }
            }
        }
    }

    suspend fun moveFile(fileUri: Uri, targetFolderUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = DocumentFile.fromSingleUri(context, fileUri)
                ?: return@withContext Result.failure(Exception("Source file not found"))
            
            val targetDir = DocumentFile.fromTreeUri(context, targetFolderUri)
                ?: return@withContext Result.failure(Exception("Target directory not found"))

            val fileName = sourceFile.name ?: "Untitled"
            val ext = fileName.substringAfterLast(".", "")
            val mimeType = getMimeType(ext)

            val newFile = targetDir.createFile(mimeType, fileName)
                ?: return@withContext Result.failure(Exception("Failed to create destination file"))

            // Copy file content
            context.contentResolver.openInputStream(fileUri).use { input ->
                context.contentResolver.openOutputStream(newFile.uri).use { output ->
                    if (input != null && output != null) {
                        input.copyTo(output)
                    }
                }
            }

            // Delete original file
            try {
                DocumentsContract.deleteDocument(context.contentResolver, fileUri)
            } catch (e: Exception) {
                // Ignore or fallback simple delete
                sourceFile.delete()
            }

            Result.success(newFile.uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSubfolder(parentUri: Uri, name: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val parentDir = DocumentFile.fromTreeUri(context, parentUri)
            val subfolder = parentDir?.createDirectory(name)
            if (subfolder != null) {
                Result.success(subfolder.uri)
            } else {
                Result.failure(Exception("Failed to create subfolder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = DocumentFile.fromSingleUri(context, uri)
            if (file != null && file.exists()) {
                val deleted = file.delete()
                if (deleted) Result.success(Unit) else Result.failure(Exception("File delete failed"))
            } else {
                // Attempt DocumentsContract delete directly as ultimate fallback
                val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                if (deleted) Result.success(Unit) else Result.failure(Exception("Document delete failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openFileWithNativeApp(uri: Uri, extension: String, customContext: Context = context) {
        try {
            val mimeType = getMimeType(extension)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customContext.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getThumbnail(uri: Uri, ext: String): Bitmap? = withContext(Dispatchers.IO) {
        if (ext.lowercase(Locale.ROOT) != "pdf") return@withContext null
        try {
            context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        return@withContext bitmap
                    }
                    renderer.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun getMimeType(ext: String): String {
        return when (ext.lowercase(Locale.ROOT)) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
    }
}
