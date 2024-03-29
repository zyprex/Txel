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
import android.os.*
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
import io.github.g0dkar.qrcode.QRCode
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Date
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    companion object {
        var ipAddress: String = "0.0.0.0"
        var ipPort: Int = 8080
        var outFile: OutFile = OutFile()
        var outDirUri: Uri = Uri.EMPTY
        var zipDirUri: Uri = Uri.EMPTY
        val handler = Handler(Looper.getMainLooper()) {
            when(it.what) {
                100, 101 -> toast(it.obj.toString())
                else -> {}
            }
            false
        }
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

        val freqUse = FreqUse(this)

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
        loadFile.setOnLongClickListener {
            freqUse.restoreFilePath()
            false
        }
        filePath.setOnLongClickListener {
            freqUse.actOnFilePath(outFile)
            false
        }

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

        loadDir.setOnLongClickListener {
            freqUse.restoreDirPath()
            false
        }
        dirPath.setOnLongClickListener {
            freqUse.actOnDirPath(outDirUri)
            false
        }

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
        var moveCount = 8
        val alphaAnimation = AlphaAnimation(0f, 0.8f).apply {
            duration = 2000
            interpolator = LinearInterpolator()
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            setAnimationListener(object : AnimationListener {
                override fun onAnimationStart(p0: Animation?) {}
                override fun onAnimationEnd(p0: Animation?) {}
                override fun onAnimationRepeat(p0: Animation?) {
                    if (moveCount < 0) {
                        screenSaverText.x = (0..screenW).random().toFloat()
                        screenSaverText.y = (0..screenH).random().toFloat()
                        moveCount = 8
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
            if(!uriQueryFileInfo(it)) return
            if (intent.type == "*/*" && outFile.size == 0L) {
                //outFile = OutFile()
                //toast("No File")
            } else {
                val filePath = findViewById<TextView>(R.id.filePath)
                filePath.text = outFile.path
                showUnzipOption()
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
        val tmpdir = applicationContext.externalCacheDir
        val toDir = File(tmpdir, outFile.name)
        ZipUtil(this).unzip(toDir, outFile.uri)
    }

    private fun showUnzipOption() {
        val unzip = findViewById<CheckBox>(R.id.unzip)
        unzip.visibility = if (outFile.path.endsWith("zip")) View.VISIBLE else View.INVISIBLE
    }
    
    private val createCacheDirZip = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val tmpdir = applicationContext.externalCacheDir ?: return@registerForActivityResult
        ZipUtil(this).zipDir(tmpdir, uri)
    }

    private val selectFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if(it != null && uriQueryFileInfo(it)) {
            val filePath = findViewById<TextView>(R.id.filePath)
            filePath.text = outFile.path
            showUnzipOption()
        }
    }

    private val selectDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) {
            val dirPath = findViewById<TextView>(R.id.dirPath)
            outDirUri = it
            dirPath.text = outDirUri.path ?: ""
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
        if (!it) toast("access files need storage permission!")
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
            R.id.customizeMimeType -> {
                val editText = EditText(this)
                editText.maxLines = 5
                editText.setText(loadOriginUsrMineTypeString())
                AlertDialog.Builder(this).apply {
                    setTitle("Customize MIME Type")
                    setMessage("Example:\n'text/plain md'")
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
                val text = HttpServer.tempText
                if (text.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip: ClipData = ClipData.newPlainText("text", text)
                    clipboard.setPrimaryClip(clip)
                    toast("copied")
                }
            }
            R.id.sendClipboard -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.text.toString()
                if (text.isNotBlank()) {
                    HttpServer.tempText = text
                    toast("send")
                }
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

