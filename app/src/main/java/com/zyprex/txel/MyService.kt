package com.zyprex.txel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD.*
import java.util.concurrent.TimeUnit

class MyService : Service() {

    companion object {
        val server = HttpServer(MainActivity.ipAddress, MainActivity.ipPort)
    }

    private val mBinder = HttpServerBinder()

    class MyTempFileManager: TempFileManager {
        private val tmpdir = MyApplication.context.externalCacheDir
        //private var tempFiles = mutableListOf<TempFile>()
        override fun clear() {
            /*for (file in tempFiles) {
                try {
                    file.delete()
                } catch (ignored: Exception) {
                    Log.e("MyService", ignored.toString())
                }
            }
            tempFiles.clear()*/
        }
        override fun createTempFile(filename_hint: String?): TempFile {
            val tempFile = DefaultTempFile(tmpdir)
            //tempFiles.add(tempFile)
            return tempFile
        }
    }

    class HttpServerBinder: Binder() {
        fun start() {
            if (!server.isAlive) {
                server.setTempFileManagerFactory{ MyTempFileManager() }
                server.start(TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS).toInt())
            }
        }
        fun stop() {
            if (server.isAlive) {
                server.stop()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onCreate() {
        super.onCreate()
        foregroundNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (server.isAlive) {
            server.stop()
        }
    }

    private fun foregroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("my_service", "my foreground service", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, 0)
        val notification = NotificationCompat.Builder(this, "my_service")
            .setContentTitle("${resources.getString(R.string.app_name)}: Http Server Running...")
            .setContentText("${MainActivity.ipAddress}:${MainActivity.ipPort}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .build()
        startForeground(1, notification)
    }
}