package com.zyprex.txel


import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import io.github.g0dkar.qrcode.ErrorCorrectionLevel
import io.github.g0dkar.qrcode.QRCode
import io.github.g0dkar.qrcode.render.Colors
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.concurrent.thread

class HttpServer(hostname: String, port: Int): NanoHTTPD(hostname, port) {

    companion object {
        var tempText  = ""
    }

    private val baseUri = "http://$hostname:$port"
    private val htmlWeaver = HtmlWeaver()
    private val tempFileList = mutableListOf<DocumentFile>()

    override fun serve(session: IHTTPSession?): Response {
        val uri = session?.uri as String
        return when(session.method) {
            Method.GET -> {
                //Log.d("HttpServer", session.parameters.toString())
                //Log.d("httpServer", uri)
                if (Regex("/l/.+").matches(uri)) {
                    return sendOutDirTree(session)
                }
                if (Regex("/z/.+").matches(uri)) {
                    return sendZipDirTree(session)
                }
                when(uri) {
                    "/" -> sendIndexPage()
                    "/d" -> sendOutFile(session)
                    "/l" -> sendOutDir()
                    "/u" -> sendUploadPage()
                    "/t" -> sendTextPage(session)
                    "/r" -> sendRawText()
                    "/q" -> sendQRCodePage()
                    "/z" -> sendZipDirPage()
                    "/z/" -> sendZipDirPage()
                    else -> send404Page()
                }

            }
            Method.POST -> {
                when(uri) {
                    "/u" -> sendUploadPage(session)
                    "/t" -> sendTextPageSync(session)
                    "/qrcode" -> sendQRCodePage(session)
                    else -> send404Page()
                }
            }
            else -> send404Page()
        }
    }


    private fun sendRawText(): Response {
        return newFixedLengthResponse(tempText)
    }

    private fun parseRangePosition(range: String, size: Long): MutableList<Long> {
        val posstr = range.replace("bytes=", "").split("-")
        val pos = mutableListOf(0L, size - 1)
        if (posstr[0].isNotEmpty()) {
            pos[0] = posstr[0].toLong()
        }
        if (posstr[1].isNotEmpty()) {
            pos[1] = posstr[1].toLong()
        }
        return pos
    }

    private fun sendOutFile(session: IHTTPSession?) : Response {
        val outFile = MainActivity.outFile
        val headers = session?.headers
        val range = headers?.get("range")
        //Log.d("HttpServer", headers.toString())
        return if (range != null) {
            val pos = parseRangePosition(range, outFile.size)
            sendRangeFile(outFile.uri, outFile.name, outFile.path, outFile.size, pos[0], pos[1])
        } else {
            sendFile(outFile.uri, outFile.name, outFile.path, outFile.size)
        }
    }

    private fun sendFile(uri: Uri, name: String, path: String, size: Long): Response {
        val resolver = MyApplication.context.contentResolver
        val mimeType = extMimeType(path)
        val response =  newFixedLengthResponse(
            Response.Status.OK,
            mimeType,
            resolver.openInputStream(uri),
            size
        )
        val filename = URLEncoder.encode(name, "UTF-8")
        val display = if (mimeType == "application/octet-stream") "attachment" else "inline"
        response.addHeader("Content-Type", "$mimeType;charset=utf-8")
        response.addHeader("Content-Disposition", "$display;filename*=UTF-8''$filename")
        return response
    }

    private fun sendRangeFile(uri: Uri, name: String, path: String, size: Long, start: Long, end: Long): Response {
        if (start !in 0 until size || end !in 0 until size) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "range not satisfiable")
        }
        val resolver = MyApplication.context.contentResolver
        val mimeType = extMimeType(path)
        val rangeLength = end - start + 1
        val response =  newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT,
            mimeType,
            resolver.openInputStream(uri)?.apply { skip(start) },
            rangeLength
        )
        val filename = URLEncoder.encode(name, "UTF-8")
        val display = if (mimeType == "application/octet-stream") "attachment" else "inline"
        response.addHeader("Content-Type", "$mimeType;charset=utf-8")
        response.addHeader("Content-Disposition", "$display;filename*=UTF-8''$filename")
        response.addHeader("Accept-Ranges", "bytes")
        //response.addHeader("Content-Length", "$rangeLength")
        response.addHeader("Content-Range", "bytes ${start}-${end}/${size}")
        return response
    }

    private fun sendOutDirTree(session: IHTTPSession?) : Response {
        val path = session?.uri?.substring(3) ?: ""
        Log.d("HttpServer", path)
        var name = ""
        if (path.isNotEmpty()) {
            name = if (path.contains("/")) {
                path.substring(path.lastIndexOf("/") + 1)
            } else {
                path
            }
        }
        var dirUri: Uri = MainActivity.outDirUri
        //Log.d("HttpServer", "root dir $dirUri")
        //Log.d("HttpServer", "path $path, name $name")
        tempFileList.find { docFile -> docFile.name == name }?.let {
            if (it.isFile) {
                val range = session?.headers?.get("range")
                return if (range != null) {
                    val pos = parseRangePosition(range, it.length())
                    sendRangeFile(it.uri, it.name ?: "", it.uri.path ?: "", it.length(), pos[0], pos[1])
                } else {
                    sendFile(it.uri, it.name ?: "", it.uri.path ?: "", it.length())
                }
            }
            if (it.isDirectory) {
                //TODO: path should query from uri
                val dir = Uri.encode("/$path")
                dirUri = "${dirUri}${dir}".toUri()
                //Log.d("HttpServer", "dir $dirUri")
                Log.d("HttpServer", "dir ${it.uri}\n${dirUri}")
                tempFileList.clear()
                tempFileList.addAll(uriListFiles(dirUri))
                return newFixedLengthResponse(Response.Status.OK, MIME_HTML,
                    htmlWeaver.fileListPage(path, tempFileList))
            }
        }

        return sendOutDir()
    }

    private fun sendOutDir() : Response {
        val dirUri: Uri = MainActivity.outDirUri

        tempFileList.clear()
        tempFileList.addAll(uriListFiles(dirUri))
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML,
            htmlWeaver.fileListPage("", tempFileList))
    }

    private fun uriListFiles(uri: Uri): MutableList<DocumentFile>  {
        if (uri == Uri.EMPTY) {
            return mutableListOf()
        }
        val documentFile = DocumentFile.fromTreeUri(MyApplication.context, uri)
        val list = documentFile?.listFiles()
        val retList = mutableListOf<DocumentFile>()
        val fileList = mutableListOf<DocumentFile>()
        val dirList = mutableListOf<DocumentFile>()
        list?.forEach {
            if (it.isFile) {
                fileList.add(it)
            }
            if (it.isDirectory) {
                dirList.add(it)
            }
        }
        retList.addAll(fileList.sortedBy(DocumentFile::getName))
        retList.addAll(dirList.sortedBy(DocumentFile::getName))
        return retList
    }

    private fun sendZipDirTree(session: IHTTPSession?): Response {
        val path = session?.uri?.substring(3) ?: ""
        val nestZipDir = File(MainActivity.zipDirUri.toFile(), path)
        if (nestZipDir.isFile) {
            val range = session?.headers?.get("range")
            return if (range != null) {
                val pos = parseRangePosition(range, nestZipDir.length())
                sendRangeFile(nestZipDir.toUri(), nestZipDir.name ,
                    nestZipDir.path, nestZipDir.length(), pos[0], pos[1])
            } else {
                sendFile(nestZipDir.toUri(), nestZipDir.name,
                    nestZipDir.path, nestZipDir.length())
            }
        } else if (nestZipDir.isDirectory) {
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML,
                htmlWeaver.zipDirPage(path, nestZipDir.toUri()))
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, path)
    }

    private fun sendZipDirPage(): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML,
            htmlWeaver.zipDirPage("",MainActivity.zipDirUri))
    }

    private fun sendIndexPage(): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlWeaver.indexPage())
    }
    private fun sendUploadPage(): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlWeaver.uploadPage(freeSpace()))
    }
    private fun sendUploadPage(session: IHTTPSession?): Response {
        if (session == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, htmlWeaver.notFoundPage())
        }
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val params = session.parameters as  MutableMap<String, List<String>>
        val tempFilePath = files["file"]
        if (tempFilePath != null) {
            //Log.d("HttpServer", tempFilePath)
            var fileName = params["file_name"]?.first()
            if (fileName != null) {
                fileName = URLDecoder.decode(fileName, "UTF-8")
            } else {
                fileName = params["file"]?.first()
            }
            //save file to Download folder
            downloadClientUploadFile(tempFilePath, fileName)
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlWeaver.uploadPage(freeSpace(), fileName ?: ""))
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlWeaver.uploadPage(freeSpace(), "///"))
    }

    private fun sendTextPage(session: IHTTPSession?): Response {
        session?.queryParameterString?.let { tempText = Uri.decode(it) }
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlWeaver.textPage(tempText))
    }

    private fun sendTextPageSync(session: IHTTPSession?): Response {
        val params = session?.parameters as MutableMap<String, List<String>>
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        tempText = params["text"]?.first() ?: ""
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlWeaver.textPage(tempText))
    }

    private fun downloadClientUploadFile(tempFilePath: String, fileName: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            thread {
                val tempFile = File(tempFilePath)
                val downFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path + File.separator + fileName)
                tempFile.copyTo(downFile, true)
                clearCache()
            }
            return
        }
        val context = MyApplication.context
        val tempFile = File(context.externalCacheDir, tempFilePath.substring(context.externalCacheDir.toString().length))
        val fileUri = Uri.fromFile(tempFile)
        thread {
            context.contentResolver.openInputStream(fileUri).use { inputStream ->
                val bis = BufferedInputStream(inputStream)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri =
                    context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            val bos = BufferedOutputStream(outputStream)
                            val buffer = ByteArray(1024)
                            var bytes = bis.read(buffer)
                            while (bytes >= 0) {
                                bos.write(buffer, 0, bytes)
                                bos.flush()
                                bytes = bis.read(buffer)
                            }
                            bos.close()
                        }
                    }
                }
            }
            clearCache()
        }
    }

    private fun send404Page() = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML,  htmlWeaver.notFoundPage())

    private fun sendQRCodePage(): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlWeaver.qrcodePage(baseUri))
    }

    private fun sendQRCodePage(session: IHTTPSession?): Response {
        if (session == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, htmlWeaver.notFoundPage())
        }
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        //val params = session.parameters as  MutableMap<String, List<String>>
        val params = session.parms as MutableMap<String, String>
        Log.d("HttpServer", params.toString())

        val data = params["data"] ?: ""
        val errLv = when (params["errLv"]) {
            "L" -> ErrorCorrectionLevel.L
            "M" -> ErrorCorrectionLevel.M
            "Q" -> ErrorCorrectionLevel.Q
            "H" -> ErrorCorrectionLevel.H
            else -> ErrorCorrectionLevel.M
        }
        val cellSize = params["cellSize"]?.toInt() ?: 25
        val margin = params["margin"]?.toInt() ?: 0
        val brightColor = params["brightColor"] ?: "#ffffff"
        val darkColor = params["darkColor"] ?: "#000000"
        val marginColor = params["marginColor"] ?: "#ffffff"
        val qrCodeGraphics = QRCode(data, errLv).render(
            cellSize = cellSize,
            margin = margin,
            brightColor = Colors.css(brightColor),
            darkColor = Colors.css(darkColor),
            marginColor = Colors.css(marginColor),
        )
        val baos = ByteArrayOutputStream()
        qrCodeGraphics.writeImage(baos)
        val bais = ByteArrayInputStream(baos.toByteArray())
        val response = newFixedLengthResponse(
            Response.Status.OK,
            "image/png",
            bais,
            qrCodeGraphics.getBytes().size.toLong()
        )
        response.addHeader("Content-Type", "image/png;charset=utf-8")
        response.addHeader("Content-Disposition", "inline;filename*=UTF-8''qrcode.png")
        return response
    }

    private fun freeSpace(): String {
        val context = MyApplication.context
        val availBytes = StatFs(context.externalCacheDir?.path).availableBytes
        return bytesToHuman(availBytes)
    }

    private fun clearCache() {
        val context = MyApplication.context
        val cacheDir = context.externalCacheDir
        cacheDir?.listFiles()?.forEach {
            it.delete()
        }
    }
}

