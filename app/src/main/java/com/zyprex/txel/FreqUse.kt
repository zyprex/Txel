package com.zyprex.txel

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class FreqUse(private val activity: MainActivity) {
    companion object {
        private const val F_URI = "file_uri.txt"
        private const val F_PATH = "file_path.txt"
        private const val D_URI = "dirs_uri.txt"
        private const val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    private val filePathUriList = readLinesAsList(F_URI)
    private val filePathList = readLinesAsList(F_PATH)
    private val dirUriList = readLinesAsList(D_URI)

    private fun readLinesAsList(filename: String): MutableList<String> {
        val list = mutableListOf<String>()
        try {
            activity.openFileInput(filename).use { fis ->
                val br = BufferedReader(InputStreamReader(fis))
                list.addAll(br.readLines().toMutableList())
                br.close()
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return list
    }

    private fun writeLinesFromList(filename: String, list: MutableList<String>) {
        try {
            activity.openFileOutput(filename, Context.MODE_PRIVATE).use { fos ->
                BufferedWriter(OutputStreamWriter(fos)).use {
                    list.forEach { str ->
                        it.write("$str\n")
                    }
                }
            }
        } catch (e: IOException){
            e.printStackTrace()
        }
    }

    private fun saveFilePath() {
        writeLinesFromList(F_URI, filePathUriList)
        writeLinesFromList(F_PATH, filePathList)
    }

    private fun saveDirUri() {
        writeLinesFromList(D_URI, dirUriList)
    }

    fun actOnFilePath(outFile: MainActivity.OutFile) {
        AlertDialog.Builder(activity).apply {
            setTitle("FreqUse: file")
            setCancelable(true)
            setMessage("add or remove file on frequently used file list.")
            setPositiveButton("add") { _, _ ->
                filePathUriList.add(outFile.uri.toString())
                filePathList.add(outFile.path)
                saveFilePath()
            }
            setNegativeButton("remove") { _, _ ->
                filePathUriList.remove(outFile.uri.toString())
                filePathList.remove(outFile.path)
                activity.findViewById<TextView>(R.id.filePath).text = ""
                MainActivity.outFile = MainActivity.OutFile()
                activity.findViewById<CheckBox>(R.id.unzip).visibility = View.INVISIBLE
                saveFilePath()
            }
        }.show()
    }

    fun restoreFilePath() {
        AlertDialog.Builder(activity).apply {
            setTitle("FreqUse: restore file")
            setCancelable(true)
            setItems(filePathList.toTypedArray()) { _, i ->
                if (uriQueryFileInfo(Uri.parse(filePathUriList[i]))) {
                    activity.findViewById<TextView>(R.id.filePath).text = MainActivity.outFile.path
                    activity.findViewById<CheckBox>(R.id.unzip).visibility =
                        if (MainActivity.outFile.path.endsWith("zip"))
                            View.VISIBLE
                        else
                            View.INVISIBLE
                }
            }
        }.show()
    }

    private fun ownUri(uri: Uri) {
        activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    private fun discardUri(uri: Uri) {
        activity.contentResolver.releasePersistableUriPermission(uri, takeFlags)
    }

    fun actOnDirPath(outDirUri: Uri) {
        AlertDialog.Builder(activity).apply {
            setTitle("FreqUse: directory")
            setCancelable(true)
            setMessage("add or remove file on frequently used dir list.")
            setPositiveButton("add") { _, _ ->
                dirUriList.add(outDirUri.toString())
                ownUri(outDirUri)
                saveDirUri()
            }
            setNegativeButton("remove") { _, _ ->
                dirUriList.remove(outDirUri.toString())
                discardUri(outDirUri)
                activity.findViewById<TextView>(R.id.dirPath).text = ""
                MainActivity.outDirUri = Uri.EMPTY
                saveDirUri()
            }
        }.show()
    }

    fun restoreDirPath() {
        AlertDialog.Builder(activity).apply {
            setTitle("FreqUse: restore directory")
            setCancelable(true)
            setItems(dirUriList.map { s -> Uri.parse(s).path }.toTypedArray()) { _, i ->
                MainActivity.outDirUri = Uri.parse(dirUriList[i])
                ownUri(MainActivity.outDirUri)
                activity.findViewById<TextView>(R.id.dirPath).text = MainActivity.outDirUri.path
            }
        }.show()
    }
}