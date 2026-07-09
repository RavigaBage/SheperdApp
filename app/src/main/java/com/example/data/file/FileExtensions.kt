package com.example.data.file

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

object FileExtensions {
    fun String.toFileColor(): Color {
        return when (this.lowercase(Locale.ROOT)) {
            "pdf" -> Color(0xFFC0392B) // Red
            "docx", "doc" -> Color(0xFF1B2B4B) // Navy / Blue
            "pptx", "ppt" -> Color(0xFFD68910) // Orange / Gold
            "txt" -> Color(0xFF2D6A4F) // Forest Green
            else -> Color(0xFF7F8C8D) // General gray
        }
    }

    fun String.toFileIcon(): ImageVector {
        return when (this.lowercase(Locale.ROOT)) {
            "pdf" -> Icons.Default.PictureAsPdf
            "docx", "doc" -> Icons.Default.Description
            "pptx", "ppt" -> Icons.Default.Slideshow
            "txt" -> Icons.Default.Article
            else -> Icons.Default.InsertDriveFile
        }
    }

    fun Long.toReadableSize(): String {
        if (this <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.ROOT, "%.1f %s", this / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
