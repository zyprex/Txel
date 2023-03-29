package com.zyprex.txel

import android.content.ContentResolver
import android.net.Uri
import android.widget.CheckBox
import androidx.core.net.toUri
import java.io.*
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread


class ZipUtil(private val activity: MainActivity) {

   private val contentResolver: ContentResolver = MyApplication.context.contentResolver

   private fun toggleUnzipCheckBox(b: Boolean) {
        activity.runOnUiThread {
            activity.findViewById<CheckBox>(R.id.unzip).isEnabled = b
        }
    }
    private fun showToast(s: String) {
        activity.runOnUiThread {
            toast(s)
        }
    }
    private fun useCacheUnzip(uri: Uri) {
        activity.runOnUiThread {
            MainActivity.zipDirUri = uri
            toast("Use cached unzip files")
        }
    }
    /* unzip uri file and output to zip name directory */
    fun unzip(toDir: File, zipFileUri: Uri) {
        thread {
            if (toDir.exists()) {
                useCacheUnzip(toDir.toUri())
                return@thread
            } else {
                toDir.mkdirs()
            }
            toggleUnzipCheckBox(false)
            var ok = true
            ZipInputStream(BufferedInputStream(
                contentResolver.openInputStream(zipFileUri))).use { zis ->
                while (true) {
                    var entry: ZipEntry
                    try {
                        entry = zis.nextEntry ?: break
                    } catch (e: UTFDataFormatException) {
                        showToast(e.toString())
                        ok = false
                        zis.closeEntry()
                        break
                    }
                    val f = File(toDir, entry.name)
                    if (f.exists()){
                        zis.closeEntry()
                        continue
                    }
                    if (entry.isDirectory) {
                        f.mkdirs()
                    } else {
                        FileOutputStream(f).use { out ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }
            MainActivity.zipDirUri = toDir.toUri()
            toggleUnzipCheckBox(true)
            if (ok)
                showToast("Zip file unzipped")
        }
    }
    /* zip a directory and output to uri */
    fun zipDir(zipFileDir: File, uri: Uri) {
        thread {
            ZipOutputStream(
                BufferedOutputStream(
                    contentResolver.openOutputStream(uri, "w"))
            ).use { zos ->
                zos.setLevel(Deflater.NO_COMPRESSION)
                zipFileDir.walkTopDown().forEach { file ->
                    var zipFileName =
                        file.absolutePath.removePrefix(zipFileDir.absolutePath).removePrefix("/")
                    zipFileName += if (file.isDirectory) "/" else ""
                    val entry = ZipEntry(zipFileName)
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().copyTo(zos)
                    }
                }
            }
            showToast("zip file created")
        }
    }
}