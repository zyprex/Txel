package com.zyprex.txel

import android.content.Context
import android.widget.Toast
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.DecimalFormat

fun assetsAsText(filename: String): String {
    val sb = StringBuilder()
    try {
        MyApplication.context.assets.open(filename).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).forEachLine {
                sb.append(it.trim())
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return sb.toString()
}

fun bytesToHuman(b: Long): String {
    val k = 1024
    val m = 1024 * k
    val g = 1024 * m
    return when (b) {
        in 0 until k -> "$b bytes"
        in k until m -> DecimalFormat("#.## KB").format(b.toDouble()/k)
        in m until g -> DecimalFormat("#.## MB").format(b.toDouble()/m)
        in g .. Long.MAX_VALUE -> DecimalFormat("#.## GB").format(b.toDouble()/g)
        else -> ""
    }
}

const val usrMineTypesFileName = "usr_mime_types.txt"

var usrMineType = mutableMapOf<String, String>()

fun refreshUsrMineType(str: String) {
    usrMineType.clear()
    str.split("\n").forEach { line ->
        if (line.length > 4 && line.contains(' ')) {
            val kv = line.split(" ")
            usrMineType[kv[0]] = kv[1]
        }
    }
}

fun saveUsrMineType(types: String) {
    writeFile(usrMineTypesFileName, types)
}

fun readUsrMineType(): MutableMap<String, String> {
    val context = MyApplication.context
    val usrMineType = mutableMapOf<String, String>()
    try {
        context.openFileInput(usrMineTypesFileName).use { fis ->
            val br = BufferedReader(InputStreamReader(fis))
            br.forEachLine {
                if (it.length > 4 && it.contains(' ')) {
                    val line = it.split(' ')
                    usrMineType[line[0]] = line[1]
                }
            }
        }
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }
    //Toast.makeText(MyApplication.context, "read: $usrMineType", Toast.LENGTH_SHORT).show()
    return usrMineType
}

fun loadOriginUsrMineTypeString(): String {
    return readFile(usrMineTypesFileName)
}

fun extMimeType(path: String): String {
    val dot = path.lastIndexOf(".")
    if (dot == -1) {
        return "application/octet-stream"
    }
    val ext = path.substring(dot + 1).lowercase()
    for (pair in usrMineType) {
        if (pair.value == ext) {
            return pair.key
        }
    }
    for (pair in extMimeMapMultiple) {
        if (pair.value.contains(ext)) {
            return pair.key
        }
    }
    for (pair in extMimeMap) {
        if (pair.value == ext) {
            return pair.key
        }
    }
    return "application/octet-stream"
}

fun writeFile(filename: String, str: String) {
    val context = MyApplication.context
    try {
        context.openFileOutput(filename, Context.MODE_PRIVATE).use { fos ->
            BufferedWriter(OutputStreamWriter(fos)).use {
                it.write(str)
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

fun readFile(filename: String): String {
    var str = ""
    val context = MyApplication.context
    try {
        context.openFileInput(filename).use { fis ->
            val br = BufferedReader(InputStreamReader(fis))
            str = br.readText()
            br.close()
        }
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return str
}

const val confFileName = "conf"

val confMap = mutableMapOf<String, String>()

fun readConf() {
    confMap.clear()
    val str = readFile(confFileName)
    str.split("\n").forEach {
        if (it.contains("\t")) {
            val kv = it.split("\t")
            confMap[kv[0]] = kv[1]
        }
    }
}

fun readConf(k: String): String? {
    return confMap[k]
}

fun saveConf() {
    val sb = StringBuilder()
    confMap.forEach {
        sb.append("${it.key}\t${it.value}\n")
    }
    writeFile(confFileName, sb.toString())
}

fun saveConf(k: String, v: String) {
    confMap[k] = v
    saveConf()
}

fun readNightMode(): String = readConf("nightMode") ?: "2"
fun saveNightMode(i: String) = saveConf("nightMode", i)
fun readPort(): String = readConf("port") ?: "8080"
fun savePort(port: String) = saveConf("port", port)
fun validPort(port: Int): Boolean =
    if (port in 1024..49151) {
        true
    } else {
        Toast.makeText(MyApplication.context, "port range: 1024~49151", Toast.LENGTH_LONG).show()
        false
    }