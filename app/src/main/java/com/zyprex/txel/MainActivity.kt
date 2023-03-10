package com.zyprex.txel

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import io.github.g0dkar.qrcode.QRCode
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Date
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        var ipAddress: String = "0.0.0.0"
        var ipPort: Int = 8080
        var outFile: OutFile = OutFile()
        var outDirUri: Uri = Uri.EMPTY
        var zipDirUri: Uri = Uri.EMPTY
    }

    data class OutFile(val uri: Uri = Uri.EMPTY, val path: String = "", val name: String = "", val size: Long = 0L)

    lateinit var httpServerBinder: MyService.HttpServerBinder

    private val connection = object : ServiceConnection {
        var alive = false
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            httpServerBinder = binder as MyService.HttpServerBinder
            httpServerBinder.start()
            alive = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            httpServerBinder.stop()
            alive = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        readConf()
        nightModeSelector(readNightMode())

        setContentView(R.layout.activity_main)

        ipPort = readPort().toInt()
        usrMineType = readUsrMineType()

        val start = findViewById<SwitchCompat>(R.id.start)
        val address = findViewById<TextView>(R.id.address)
        val port = findViewById<EditText>(R.id.port)
        val loadFile = findViewById<Button>(R.id.loadFile)
        val filePath = findViewById<TextView>(R.id.filePath)
        val unzip = findViewById<CheckBox>(R.id.unzip)
        val loadDir = findViewById<Button>(R.id.loadDir)
        val dirPath = findViewById<TextView>(R.id.dirPath)

        val keepScreenOn = findViewById<CheckBox>(R.id.keepScreenOn)
        val screenSaver = findViewById<Button>(R.id.screenSaver)

        val qrcodeImage = findViewById<ImageView>(R.id.qrcodeImage)
        val qrcodeBlock = findViewById<LinearLayout>(R.id.qrcodeBlock)

        val addressOriginColor = address.currentTextColor
        val portOriginColor = address.currentTextColor

        port.setText(ipPort.toString())

        fun uiChangeReset() {
            start.isChecked = false
            address.text = "http://0.0.0.0:"
            port.isEnabled = true
            address.setTextColor(addressOriginColor)
            port.setTextColor(portOriginColor)

            qrcodeImage.setImageDrawable(null)
            qrcodeBlock.visibility = View.INVISIBLE
        }

        fun uiChange() {
            start.isChecked = true
            address.text = "http://$ipAddress:"
            var portNum = ipPort
            val portStr = port.text.toString()
            if (portStr != "") {
                portNum = portStr.toInt()
            }
            if (validPort(portNum)) {
                ipPort = portNum
            } else {
                port.setText(ipPort.toString())
            }
            port.isEnabled = false
            address.setTextColor(ContextCompat.getColor(this, R.color.green))
            port.setTextColor(ContextCompat.getColor(this, R.color.green))
            generateQRCode("http://$ipAddress:$ipPort")
        }

        address.setOnClickListener {
            if (start.isChecked) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$ipAddress:$ipPort")))
            }
        }

        start.setOnCheckedChangeListener { _, b ->
            if (b) {
                ipAddress = ipAddress()
                uiChange()
                bindService(Intent(this, MyService::class.java), connection, BIND_AUTO_CREATE)
            } else {
                uiChangeReset()
                unbindService(connection)
            }
        }

        loadFile.setOnClickListener { selectFile.launch(arrayOf("*/*")) }
        filePath.text = outFile.path

        unzip.setOnCheckedChangeListener { _, b ->
            if (b) {
                unzipFile(outFile)
            } else {
                zipDirUri = Uri.EMPTY
                File(applicationContext.externalCacheDir, outFile.name).deleteRecursively()
                toast("Zip file deleted")
            }
        }
        if (outFile.name.endsWith("zip")) {
            unzip.visibility = View.VISIBLE
            if (File(applicationContext.externalCacheDir, outFile.name).exists()) {
                unzip.isChecked = true
            }
        }

        loadDir.setOnClickListener { selectDir.launch(outDirUri) }
        dirPath.text = outDirUri.path ?: ""

        keepScreenOn.setOnCheckedChangeListener { _, b ->
            if (b) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        if (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            == WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) {
            keepScreenOn.isChecked = true
        }

        val mainConfig = findViewById<LinearLayout>(R.id.mainConfig)

        val screenSaverText = findViewById<TextView>(R.id.screenSaverText)
        val screenW = screenRealWidth() - 120
        val screenH = screenRealHeight() - 60
        var moveCount = 5
        val alphaAnimation = AlphaAnimation(0f, 0.8f).apply {
            duration = 2000
            interpolator = LinearInterpolator()
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            setAnimationListener(object : AnimationListener {
                override fun onAnimationStart(p0: Animation?) {}
                override fun onAnimationEnd(p0: Animation?) {}
                override fun onAnimationRepeat(p0: Animation?) {
                    if (moveCount == 0) {
                        screenSaverText.x = (0..screenW).random().toFloat()
                        screenSaverText.y = (0..screenH).random().toFloat()
                        moveCount = 5
                    } else {
                        moveCount--
                    }
                }
            })
        }

        val screenSaverLayout = findViewById<RelativeLayout>(R.id.screenSaverLayout)
        screenSaverLayout.layoutParams.height = screenRealHeight()
        screenSaver.setOnClickListener {
            supportActionBar?.hide()
            mainConfig.visibility = View.GONE
            screenSaverLayout.visibility = View.VISIBLE
            screenSaverText.startAnimation(alphaAnimation)
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        screenSaverLayout.setOnClickListener {
            supportActionBar?.show()
            mainConfig.visibility = View.VISIBLE
            screenSaverLayout.visibility = View.GONE
            screenSaverText.clearAnimation()
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        supportActionBar?.setDisplayShowCustomEnabled(true)

        findViewById<Button>(R.id.openDetail).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        qrcodeImage.setOnLongClickListener {
            if (qrcodeImage.drawable != null) {
                toast("save qrcode")
                saveQRCode.launch("qrcode-${Date().time}.png")
            }
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (connection.alive) {
            unbindService(connection)
        }
    }

    override fun onStart() {
        super.onStart()
        //intentAction(intent)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            getStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null)
            intentAction(intent)
    }

    private fun intentAction(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain")  {
                    handleSendText(intent)
                } else {
                    handleSendFile(intent)
                }
            }
            else -> {
                // handle other intents, such as being started from the home screen
            }
        }
    }

    private fun handleSendText(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text != null) {
            HttpServer.tempText = text
            toast("Text")
        } else {
            handleSendFile(intent)
        }
    }

    private fun handleSendFile(intent: Intent) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri?)?.let {
            if(!uriQueryFileInfo(it)) {
                return
            }
            if (intent.type == "*/*" && outFile.size == 0L) {
                outFile = OutFile()
            } else {
                val filePath = findViewById<TextView>(R.id.filePath)
                filePath.text = outFile.path
                toast("File")
            }
        }
    }

    private fun generateQRCode(text: String) {
        val qrcodeImage = findViewById<ImageView>(R.id.qrcodeImage)
        val qrcodeBlock = findViewById<LinearLayout>(R.id.qrcodeBlock)
        try {
            thread {
                val qrCodeGraphics = QRCode(text).render(10, 0)
                val qrCodeBitmap: Bitmap?
                if (qrCodeGraphics.width > screenWidth()) {
                    val option = BitmapFactory.Options()
                    option.inSampleSize = largeBitmapSampleSize(qrCodeGraphics.width)
                    qrCodeBitmap = BitmapFactory.decodeStream(qrCodeGraphics.nativeImage() as InputStream, null, option)
                } else {
                    qrCodeBitmap = qrCodeGraphics.nativeImage() as Bitmap
                }
                runOnUiThread {
                    qrcodeImage.setImageBitmap(qrCodeBitmap)
                    qrcodeBlock.visibility = View.VISIBLE
                }
            }
        } catch (e : Exception) {
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }
    }

    private fun largeBitmapSampleSize(width: Int): Int {
        var sampleSize = 1
        var w = width
        val sw = screenWidth()
        while (w > sw) {
            w -= sw
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun unzipFile(outFile: OutFile) {
        thread {
            val tmpdir = applicationContext.externalCacheDir
            val zipNameDir = File(tmpdir, outFile.name)
            if (!zipNameDir.exists()) {
                zipNameDir.mkdirs()
                runOnUiThread {
                    val unzip = findViewById<CheckBox>(R.id.unzip)
                    unzip.isEnabled = false
                }
            } else {
                zipDirUri = zipNameDir.toUri()
                runOnUiThread {
                    toast("Use cached unzip files")
                }
                return@thread
            }
            var ok = true
            ZipInputStream(BufferedInputStream(contentResolver.openInputStream(outFile.uri))).use { input ->
                while (true) {
                    var entry:ZipEntry
                    try {
                        entry = input.nextEntry ?: break
                    } catch (e: UTFDataFormatException) {
                        runOnUiThread {
                            toast("ERROR: stop unzip due to bad data format")
                        }
                        ok = false
                        input.closeEntry()
                        break
                    }
                    //Log.d("MainActivity", entry.name)
                    val f = File(zipNameDir, entry.name)
                    if (f.exists()) {
                        input.closeEntry()
                        continue
                    }
                    if (entry.isDirectory) {
                        f.mkdirs()
                    } else {
                        FileOutputStream(f).use { out ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (input.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                    }
                    input.closeEntry()
                }
            }
            zipDirUri = zipNameDir.toUri()
            runOnUiThread {
                val unzip = findViewById<CheckBox>(R.id.unzip)
                unzip.isEnabled = true
                if (ok)
                    toast("Zip file unzipped")
            }
        }
    }
    
    private val createCacheDirZip = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        val tmpdir = applicationContext.externalCacheDir ?: return@registerForActivityResult
        thread {
            ZipOutputStream(
                BufferedOutputStream(
                    applicationContext.contentResolver.openOutputStream(uri, "w")
                )
            ).use { zos ->
                zos.setLevel(Deflater.NO_COMPRESSION)
                tmpdir.walkTopDown().forEach { file ->
                    var zipFileName =
                        file.absolutePath.removePrefix(tmpdir.absolutePath).removePrefix("/")
                    zipFileName += if (file.isDirectory) "/" else ""
                    //Log.d("MainActivity" , zipFileName)
                    val entry = ZipEntry(zipFileName)
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().copyTo(zos)
                    }
                }
            }
            runOnUiThread {
                toast("zip file created")
            }
        }
    }

    private val selectFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            val filePath = findViewById<TextView>(R.id.filePath)
            uriQueryFileInfo(it)
            filePath.text = outFile.path
            val unzip = findViewById<CheckBox>(R.id.unzip)
            if (outFile.path.endsWith("zip")) {
                unzip.visibility = View.VISIBLE
            } else {
                unzip.visibility = View.INVISIBLE
            }
        }
    }

    private val selectDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) {
            val dirPath = findViewById<TextView>(R.id.dirPath)
            outDirUri = it
            dirPath.text = outDirUri.path ?: ""
        /*  val contentResolver = applicationContext.contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, takeFlags) */
        }
    }

    private val saveQRCode = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        applicationContext.contentResolver.openFileDescriptor(uri, "w")?.use { fd ->
            FileOutputStream(fd.fileDescriptor).use {
                val qrcodeBitmap = findViewById<ImageView>(R.id.qrcodeImage).drawable.toBitmap()
                qrcodeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private val getStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!it) {
            toast("access files need storage permission!")
        }
    }

    private fun ipAddress(): String {
        try {
            for (networkInterfaces in NetworkInterface.getNetworkInterfaces()) {
                for(inetAddress in networkInterfaces.inetAddresses) {
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress) {
                        if (inetAddress is Inet4Address) {
                            return inetAddress.hostAddress ?: "localhost"
                        }
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e("MainActivity", e.toString())
        }
        return "localhost"
    }

    private fun uriQueryFileInfo(uri: Uri): Boolean {
        var ret = false
        val cursor  = contentResolver.query(uri,
        arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
        ), null, null, null)
        cursor?.use {
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            while (it.moveToNext()) {
                if (outFile.uri == uri) break
                outFile = OutFile(
                    uri = uri,
                    path = it.getString(pathColumn),
                    name = it.getString(nameColumn),
                    size = it.getLong(sizeColumn),
                )
                ret = true
            }
        }
        return ret
    }

    //private fun screenHeight() = resources.displayMetrics.heightPixels
    private fun screenWidth() = resources.displayMetrics.widthPixels
    private fun screenSize(): DisplayMetrics {
        val dm = DisplayMetrics()
        val windowManager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm
    }
    private fun screenRealWidth(): Int = screenSize().widthPixels
    private fun screenRealHeight(): Int = screenSize().heightPixels

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.option_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nightMode -> {
                AlertDialog.Builder(this).apply {
                    setTitle("Night Mode")
                    setItems(arrayOf("Day", "Night", "Auto")) { _, i ->
                        when(i) {
                            0 -> {
                                saveNightMode("0")
                                nightModeSelector("0")
                            }
                            1 -> {
                                saveNightMode("1")
                                nightModeSelector("1")
                            }
                            2 -> {
                                saveNightMode("2")
                                nightModeSelector("2")
                            }
                        }
                    }
                }.show()
            }
            R.id.changeDefaultPort -> {
                var keepOn = false
                val start = findViewById<SwitchCompat>(R.id.start)
                if (start.isChecked) {
                    start.performClick()
                    keepOn = true
                }
                val editText = EditText(this)
                editText.maxLines = 1
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.setText(ipPort.toString())
                editText.selectAll()
                AlertDialog.Builder(this).apply {
                    setTitle("Change Default Port")
                    setMessage("Possible port range: 1024~49151")
                    setView(editText)
                    setPositiveButton("Ok") { _, _ ->
                        val str = editText.text.toString()
                        if (str != "") {
                            val portNum = str.toInt()
                            if (validPort(portNum)) {
                                val port = findViewById<EditText>(R.id.port)
                                port.setText(str)
                                ipPort = portNum
                                savePort(str)
                            }
                            if (keepOn) {
                                start.performClick()
                            }
                        }
                    }
                    setNegativeButton("Cancel") { _, _ ->
                        if (keepOn) {
                            start.performClick()
                        }
                    }
                }.show()
            }
            R.id.userDefMimeType -> {
                val editText = EditText(this)
                editText.maxLines = 5
                editText.setText(loadOriginUsrMineTypeString())
                AlertDialog.Builder(this).apply {
                    setTitle("User Defined Mime Type")
                    setMessage("Example: 'text/plain html'\nThis line will let any '*.html' file show as text in browser.")
                    setView(editText)
                    setPositiveButton("Ok") { _, _ ->
                        val str = editText.text.toString()
                        refreshUsrMineType(str)
                        saveUsrMineType(str)
                    }
                    setNegativeButton("Cancel", null)
                }.show()
            }
            R.id.copySavedText -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData = ClipData.newPlainText("text", HttpServer.tempText)
                clipboard.setPrimaryClip(clip)
                toast("copied")
            }
            R.id.createCacheZip -> {
                createCacheDirZip.launch("txel-cache.zip")
            }
            else -> {}
        }
        return super.onOptionsItemSelected(item)
    }

    private fun nightModeSelector(i: String?) {
        when (i) {
            "0" -> delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
            "1" -> delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
            "2" -> delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
