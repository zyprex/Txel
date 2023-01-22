package com.zyprex.txel

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class HtmlWeaver {
    companion object {
        private val indexPage = assetsAsText("txel_index.html")
        private val uploadPage = assetsAsText("txel_upload.html")
        private val textPage = assetsAsText("txel_text.html")
        private val fileListPage = assetsAsText("txel_file_list.html")
        private val msgPage = assetsAsText("txel_msg.html")
        private val qrcodePage = assetsAsText("txel_qrcode.html")
    }

    fun indexPage(): String {
        return indexPage.replace("{{index}}", makeIndex())
    }

    fun fileListPage(path: String, fileList: MutableList<DocumentFile>): String {
        return fileListPage.replace("{{file_list}}", makeFileList(path, fileList))
    }
    private fun makeFileList(path: String, fileList: MutableList<DocumentFile>): String {
        val sb = StringBuilder()
        sb.append("<tr><th>Name</th><th>Size</th><th>Type</th></tr>")
        fileList.forEach {
            val filePath = if (path != "") {
                Uri.encode("$path/${it.name}")
            } else {
                it.name?:""
            }
            val mimeType = extMimeType(it.uri.path ?: "")

            sb.append("<tr>")
            sb.append("<td>")
            if (mimeType.startsWith("image/")) {
                sb.append(
                    tag(
                        "a",
                        listOf("href='/l/${filePath}'", "class='image-link'"),
                        it.name ?: ""
                    )
                )
                sb.append(
                    tag(
                        "img",
                        listOf("src='/l/${filePath}'",
                            "alt='${it.name}'", "loading='lazy'", "hidden", "class='image'")
                    )
                )
            } else {
                sb.append(
                    tag(
                        "a",
                        listOf("href='/l/${filePath}'"),
                        if (it.isDirectory) "${it.name}/" else it.name ?: ""
                    )
                )
            }
            sb.append("</td>")
            sb.append("<td>")
            sb.append(bytesToHuman(it.length()))
            sb.append("</td>")
            sb.append("<td>")
            if (it.isDirectory) {
                sb.append("---")
            } else {
                sb.append(mimeType)
            }
            sb.append("</td>")
            sb.append("</tr>")
        }
        return sb.toString()
    }

    fun notFoundPage(): String {
        return msgPage.replace("{{msg}}", "404 Not Found")
    }
    fun uploadPage(remoteDiskFree: String = "", lastUploadFile: String = ""): String {
        return uploadPage
            .replace("{{remote_disk_free}}",
                tag("p", listOf(""), "remote storage free: $remoteDiskFree"))
            .replace("{{last_upload_file}}",
                if (lastUploadFile.isNotEmpty()) {
                    if (lastUploadFile == "///") {
                        tag("p", listOf("class='upf failed'"), "Last upload failed")
                    } else {
                        tag("p", listOf("class='upf ok'"), "Last upload file: $lastUploadFile")
                    }
                } else "")
    }
    fun textPage(txt: String = ""): String {
        return textPage
            .replace("{{txt}}", txt)
            .replace("{{link}}", makeLinks(txt))
    }

    fun qrcodePage(data: String): String {
        return qrcodePage.replace("{{default_data}}", data)
    }

    private fun tag(name: String, attr: List<String> = listOf(""), inner: String = "") = "<$name ${attr.joinToString(" ")}>$inner</$name>"
    private fun tag(name: String, attr: List<String> = listOf("")) = "<$name ${attr.joinToString(" ")}/>"

    private fun makeIndex(): String {
        val sb = StringBuilder()
        if (MainActivity.outFile.uri != Uri.EMPTY) {
            sb.append(tag("a", listOf("href='/d'"), "Download"))
        } else {
            sb.append(tag("a", listOf("href=''"), "..."))
        }
        if (MainActivity.outDirUri != Uri.EMPTY) {
            sb.append(tag("a", listOf("href='/l'"), "File List"))
        }
        sb.append(tag("a", listOf("href='/u'"), "Upload"))
        sb.append(tag("a", listOf("href='/t'"), "Text"))
        sb.append(tag("a", listOf("href='/q'"), "QRCode"))
        return sb.toString()
    }
    private fun makeLinks(txt: String): String {
        val pattern = "https?://\\S+".toRegex()
        val urls = pattern.findAll(txt)
        if (urls.count() > 0) {
            val sb = StringBuilder()
            urls.forEach { url ->
                val link = "<a href='${url.value}'>${url.value}</a>"
                sb.append(link)
            }
            return sb.toString()
        }
        return ""
    }
}