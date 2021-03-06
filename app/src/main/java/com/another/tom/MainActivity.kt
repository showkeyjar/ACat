package com.another.tom

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.SoundPool
import android.os.*
import android.text.Html
import android.text.Html.FROM_HTML_MODE_LEGACY
import android.text.Spanned
import android.text.SpannedString
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.umeng.commonsdk.UMConfigure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.nio.ByteBuffer
import java.util.Collections.frequency
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.experimental.and


typealias LumaListener = (signal: Int) -> Unit


class MainActivity : AppCompatActivity(){
    val PREFS_NAME = "user_conditions"
    private val coroutineScope = CoroutineScope( Dispatchers.Main )
    private var sv: SurfaceView? = null
    //SurfaceView的句柄 控制 SurfaceView ； 持有，查找 等
    private var holder: SurfaceHolder? = null
    //矩形
    private var dst: Rect? = null
    //第一项 cymbal
    private val cymbal = arrayOf<Any>("cymbal_", 13)
    //第二项 scratch
    private val scratch = arrayOf<Any>("scratch_", 56)
    //第三项 pie
    private val pie = arrayOf<Any>("pie_", 24)
    //第四项 fart
    private val fart = arrayOf<Any>("fart_", 28)
    //第五项 drink
    private val drink = arrayOf<Any>("drink_", 81)
    //第六项 eat
    private val eat = arrayOf<Any>("eat_", 40)
    //踩脚
    private val knockout = arrayOf<Any>("knockout_", 81)
    //临时图片
    private var temp: Array<Any>? = null
    //声音池 短声音播放
    private var soundPool: SoundPool? = null
    //是否正在播放动画
    private var isPlaying = false
    //声音池id集合
    private var soundIds: ArrayList<Int>? = ArrayList()
    //声音资源文件 顺序要和图片 顺序一致
    private val resids = intArrayOf(
        R.raw.cymbal,
        R.raw.scratch,
        R.raw.pie,
        R.raw.fart,
        R.raw.drink,
        R.raw.eat,
        R.raw.knockout
    )
    //音频池的id
    private var index = 0
    //每个项的声音延迟  毫秒  按每项的顺序
    private val delay = intArrayOf(0, 2000, 1000, 200, 5000, 2000, 0)

    override fun onCreateDialog(id: Int): Dialog? {
        // show disclaimer....
        // for example, you can show a dialog box...
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        val disclaimer: Spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(getString(R.string.disclaimer), FROM_HTML_MODE_LEGACY)
        } else {
            SpannedString(Html.fromHtml(getString(R.string.disclaimer)))
        }
        builder.setMessage(disclaimer)
            .setCancelable(false)
            .setPositiveButton(
                getString(R.string.btn_agree),
                DialogInterface.OnClickListener { dialog, id -> // and, if the user accept, you can execute something like this:
                    // We need an Editor object to make preference changes.
                    // All objects are from android.context.Context
                    val settings = getSharedPreferences(PREFS_NAME, 0)
                    val editor = settings.edit()
                    editor.putBoolean("accepted", true)
                    // Commit the edits!
                    editor.commit()
                })
            .setNegativeButton(getString(R.string.btn_disagree),
                DialogInterface.OnClickListener { dialog, id ->
                    // nm.cancel(R.notification.running) // cancel the NotificationManager (icon)
                    System.exit(0)
                })
        return builder.create()
    }

    /*
    https://developer.android.com/codelabs/camerax-getting-started?hl=zh-cn#5
    https://developer.android.com/training/camerax/analyze
    目前 CameraX 不支持直接对video进行读取分析，只能先使用 ImageAnalysis
    https://developer.android.com/guide/topics/media/mediarecorder
    对音频进行处理
    */
    // private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private class LuminosityAnalyzer(private val listener: LumaListener, private val con:Context) : ImageAnalysis.Analyzer {
        private lateinit var py: Python
        // private lateinit var con: Context

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        private fun byteArrayToString(inputFace: ByteArray): String {
            return Base64.encodeToString(inputFace, Base64.DEFAULT)
        }

        override fun analyze(image: ImageProxy) {
            if (! Python.isStarted()) {
                Python.start(AndroidPlatform(con))
                Log.i("Python","python start")
            }
            if(!::py.isInitialized){
                py = Python.getInstance()
            }
            var react = 0
            // Log.i("planes size", image.planes.size.toString())
            if (image.format == PixelFormat.RGBA_8888){
                val byteRGBA = image.planes[0].buffer.toByteArray()
                val encodingRGBA = byteArrayToString(byteRGBA)
                // 0:R 红,1:G 绿,2:B 蓝,3:A 透明度
                try {
                    react = py.getModule("cat").callAttr("see_rgba", encodingRGBA).toInt()
                }catch (e: Exception) {
                    Log.i("error", e.toString())
                }
            }
            // val pixels = data.map { it.toInt() and 0xFF }
            // val luma = pixels.average()
            listener(react)
            image.close()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
//            val preview = Preview.Builder()
//                .build()
//                .also {
//                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
//                }
            var prevAction = 0
            // 系统默认 YUV420_888 格式介绍: https://www.jianshu.com/p/944ede616261 使用 setOutputImageFormat转换格式
            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer({ signal ->
                        // Log.d(TAG, "brain action: $luma")
                        if(signal!=prevAction){
                            reAction(signal)
                            prevAction = signal
                        }
                    }, applicationContext))
                }

            // Select front camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                // cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalyzer)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /*
    https://stackoverflow.com/questions/19145213/android-audio-capture-silence-detection/19752120
     */
    var audioStarted = false
    var recordTask: RecordAudio? = null

    class RecordAudio(private val started:Boolean, private val context: Context) : AsyncTask<Void?, Double?, Void?>() {
        private val TAG: String? = "listen"
        private val RECORDER_BPP = 16
        private val AUDIO_RECORDER_FILE_EXT_WAV = ".wav"
        private val AUDIO_RECORDER_FOLDER = "com.another.tom"
        private val AUDIO_RECORDER_TEMP_FILE = "record_temp.raw"

        var audioRecord:AudioRecord? = null
        var bufferSize = 0
        var frequency = 44100 //8000;

        var channelConfiguration: Int = AudioFormat.CHANNEL_IN_MONO
        var audioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT

        var threshold: Short = 15000
        var os: FileOutputStream? = null

        override fun doInBackground(vararg arg0: Void?): Void? {
            Log.w(TAG, "doInBackground")
            try {
                val filename = getTempFilename()
                try {
                    os = FileOutputStream(filename)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
                bufferSize = AudioRecord.getMinBufferSize(
                    frequency,
                    channelConfiguration, audioEncoding
                )
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return null
                }
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC, frequency,
                    channelConfiguration, audioEncoding, bufferSize
                )
                val buffer = ShortArray(bufferSize)
                audioRecord?.startRecording()
                while (started) {
                    val bufferReadResult = audioRecord!!.read(buffer, 0, bufferSize)
                    if (AudioRecord.ERROR_INVALID_OPERATION != bufferReadResult) {
                        //check signal
                        //put a threshold
                        val foundPeak = searchThreshold(buffer, threshold)
                        if (foundPeak > -1) { //found signal
                            //record signal
                            val byteBuffer = shortToByte(buffer, bufferReadResult)
                            try {
                                os?.write(byteBuffer)
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        } else { //count the time
                            //don't save signal
                        }
                        //show results
                        //here, with publichProgress function, if you calculate the total saved samples,
                        //you can optionally show the recorded file length in seconds:      publishProgress(elsapsedTime,0);
                    }
                }
                audioRecord!!.stop()

                //close file
                try {
                    os?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                copyWaveFile(getTempFilename(), getFilename())
                deleteTempFile()
            } catch (t: Throwable) {
                t.printStackTrace()
                Log.e("AudioRecord", "Recording Failed")
            }
            return null
        } //fine di doInBackground

        private fun shortToByte(input: ShortArray, elements: Int): ByteArray {
            var byteIndex = 0
            val buffer = ByteArray(elements * 2)
            var shortIndex = byteIndex
            while ( /*NOP*/shortIndex != elements /*NOP*/) {
                buffer[byteIndex] = (input[shortIndex] and 0x00FF).toByte()
                buffer[byteIndex + 1] = ((input[shortIndex] and 0xFF00.toShort()) as Int shr 8) as Byte
                ++shortIndex
                byteIndex += 2
            }
            return buffer
        }

        private fun searchThreshold(arr: ShortArray, thr: Short): Int {
            var peakIndex = 0
            val arrLen = arr.size
            while (peakIndex < arrLen) {
                if (arr[peakIndex] >= thr || arr[peakIndex] <= -thr) {
                    //se supera la soglia, esci e ritorna peakindex-mezzo kernel.
                    return peakIndex
                }
                peakIndex++
            }
            return -1 //not found
        }

        /*
    @Override
    protected void onProgressUpdate(Double... values) {
        DecimalFormat sf = new DecimalFormat("000.0000");
        elapsedTimeTxt.setText(sf.format(values[0]));

    }
    */
        private fun getFilename(): String {
            val filepath = Environment.getExternalStorageDirectory().path
            val file = File(filepath, AUDIO_RECORDER_FOLDER)
            if (!file.exists()) {
                file.mkdirs()
            }
            return file.absolutePath + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV
        }

        private fun getTempFilename(): String {
            val filepath = Environment.getExternalStorageDirectory().path
            val file = File(filepath, AUDIO_RECORDER_FOLDER)
            if (!file.exists()) {
                file.mkdirs()
            }
            val tempFile = File(filepath, AUDIO_RECORDER_TEMP_FILE)
            if (tempFile.exists()) tempFile.delete()
            return file.absolutePath + "/" + AUDIO_RECORDER_TEMP_FILE
        }

        private fun deleteTempFile() {
            val file = File(getTempFilename())
            file.delete()
        }

        private fun copyWaveFile(inFilename: String, outFilename: String) {
            val longSampleRate = frequency.toLong()
            val channels = 1
            val byteRate: Long = (RECORDER_BPP * frequency * channels / 8) as Long
            val data = ByteArray(bufferSize)
            try {
                var `in` = FileInputStream(inFilename)
                var out = FileOutputStream(outFilename)
                var totalAudioLen = `in`.getChannel().size()
                var totalDataLen = totalAudioLen + 36
                writeWaveFileHeader(
                    out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate
                )
                while (`in`.read(data) !== -1) {
                    out.write(data)
                }
                `in`.close()
                out.close()
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        @Throws(IOException::class)
        private fun writeWaveFileHeader(
            out: FileOutputStream?, totalAudioLen: Long,
            totalDataLen: Long, longSampleRate: Long, channels: Int,
            byteRate: Long
        ) {
            val header = ByteArray(44)
            header[0] = 'R'.toByte() // RIFF/WAVE header
            header[1] = 'I'.toByte()
            header[2] = 'F'.toByte()
            header[3] = 'F'.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = (totalDataLen shr 8 and 0xff).toByte()
            header[6] = (totalDataLen shr 16 and 0xff).toByte()
            header[7] = (totalDataLen shr 24 and 0xff).toByte()
            header[8] = 'W'.toByte()
            header[9] = 'A'.toByte()
            header[10] = 'V'.toByte()
            header[11] = 'E'.toByte()
            header[12] = 'f'.toByte() // 'fmt ' chunk
            header[13] = 'm'.toByte()
            header[14] = 't'.toByte()
            header[15] = ' '.toByte()
            header[16] = 16 // 4 bytes: size of 'fmt ' chunk
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // format = 1
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (longSampleRate and 0xff).toByte()
            header[25] = (longSampleRate shr 8 and 0xff).toByte()
            header[26] = (longSampleRate shr 16 and 0xff).toByte()
            header[27] = (longSampleRate shr 24 and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = (byteRate shr 8 and 0xff).toByte()
            header[30] = (byteRate shr 16 and 0xff).toByte()
            header[31] = (byteRate shr 24 and 0xff).toByte()
            header[32] = (channels * 16 / 8).toByte() // block align
            header[33] = 0
            header[34] = RECORDER_BPP.toByte() // bits per sample
            header[35] = 0
            header[36] = 'd'.toByte()
            header[37] = 'a'.toByte()
            header[38] = 't'.toByte()
            header[39] = 'a'.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = (totalAudioLen shr 8 and 0xff).toByte()
            header[42] = (totalAudioLen shr 16 and 0xff).toByte()
            header[43] = (totalAudioLen shr 24 and 0xff).toByte()
            out?.write(header, 0, 44)
        }
    } //Fine Classe RecordAudio (AsyncTask)


    fun resetAquisition() {
        Log.w(TAG, "resetAquisition")
        stopAquisition()
        //startButton.setText("WAIT");
        startAquisition()
    }

    fun stopAquisition() {
        Log.w(TAG, "stopAquisition")
        if (audioStarted) {
            audioStarted = false
            recordTask?.cancel(true)
        }
    }

    fun startAquisition() {
        Log.w(TAG, "startAquisition")
        val handler = Handler()
        handler.postDelayed(Runnable {
            //elapsedTime=0;
            audioStarted = true
            recordTask = RecordAudio(audioStarted, baseContext)
            recordTask!!.execute()
            //startButton.setText("RESET");
        }, 500)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 创建activity时调用的方法
     * 进行初始化的操作
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // viewBinding = ActivityMainBinding.inflate(layoutInflater)
        // setContentView(viewBinding.root)
        //加载布局文件 设置显示的内容
        setContentView(R.layout.activity_main)

        //通过id查找视图 只会在上面设置的内容中查找  surfaceView 特点必须自己先创建完 才能执行 ，可以通过句柄得知是否初始化完成
        sv = findViewById<View>(R.id.surface) as SurfaceView
        holder = sv!!.holder as SurfaceHolder
        //添加回调
        (holder as SurfaceHolder).addCallback(callback)

        //参数1 最多几个声音 参数2 声音类型 AudioManager是声音管理 各种静态类型
        soundPool = SoundPool.Builder().setMaxStreams(7).build()
        //遍历音频资源数组 加载到声音池
        Log.e("tag", "======init=========$soundPool")
        for (a in resids) {
            //加载一个声音，加载到声音池 参数1 上下文，参数2. 音频资源id  参数3 播放的优先级\
            //返回一个 id 音频在声音池的id
            val id = soundPool!!.load(this@MainActivity, a, 1)
            //将声音池的id添加到 集合
            soundIds?.add(id)
        }

        val settings = getSharedPreferences(PREFS_NAME, 0)
        val accepted = settings.getBoolean("accepted", false)

        if( accepted ){
            // Request camera permissions
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
            cameraExecutor = Executors.newSingleThreadExecutor()

            coroutineScope.launch {
                //接收音频输入
//                var mStartRecording = true
//                onRecord(mStartRecording) // 加入后会导致应用崩溃
                startAquisition()
            }

            UMConfigure.preInit(this, "620ca9e3226836222741786e", "Umeng")
            UMConfigure.init(this, "620ca9e3226836222741786e", "Umeng", UMConfigure.DEVICE_TYPE_PHONE, "")
        }else{
            showDialog(0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopAquisition()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    /**
     * 回调
     * 作用：
     * 告诉 holder surface是否创建完了
     */
    private val callback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        /**
         * 通知 surface 已经创建完了
         * @param holder
         */
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.i("tag", "======begin set surfaceView =========")

            //获取surfaceView 的宽高
            val svWidth = sv!!.width
            val svHeight = sv!!.height
            Log.i("tag", "$svWidth======surfaceCreated=========$svHeight")
            //目标矩形 画布上的矩形
            dst = Rect(0, 0, svWidth, svHeight)

            drawBitmap(R.mipmap.cymbal_12, holder)
            Log.e("tag", "============over=============")
        }

        /**
         * 通知 surface 发生了改变  尺寸发生变化时
         * @param holder
         * @param format
         * @param width 宽度
         * @param height 高度
         */
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        /**
         * 通知 surface已经销毁了
         * @param holder
         */
        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

    /**
     * 根据id获取 位图
     * @param id
     * @return
     */
    private fun getBitmap(id: Int): Bitmap {
        //获取到资源中的可绘制资源(图片) 参数 资源id
        val drawable = ResourcesCompat.getDrawable(getResources(), id, null)
        //子类型； Drawable 有多种类型的 例如 颜色绘制的图
        val bmpDaw = drawable as BitmapDrawable
        //获取可绘制资源中的位图
        return bmpDaw.bitmap
    }

    /**
     * 绘制图片
     *
     */
    private fun drawBitmap(id: Int, holder: SurfaceHolder?) {
        //锁定并拿到画布
        val canvas = holder!!.lockCanvas()
        val bmp = getBitmap(id)
        //获取位图的像素宽高
        val bmpWidth = bmp.width
        val bmpHeight = bmp.height

        //创建矩形 要绘制的位图
        val src = Rect(0, 0, bmpWidth, bmpHeight)
        /**
         * 画一个位图 图片对应到程序中就是位图
         * 参数
         * bmp 图片位图
         * src，要绘制的矩形 位图矩形
         * dst  目标矩形 画布中的
         * null 画笔  现在不需要
         */
        canvas.drawBitmap(bmp, src, dst!!, null)

        //绘制完毕 接触画布的锁定 并且 寄送画布
        holder!!.unlockCanvasAndPost(canvas)
    }

    private fun catPlay(id:Int){
        if (isPlaying) {
            // Toast.makeText(this@MainActivity, "再快点屏就戳烂了，慢点戳！", Toast.LENGTH_SHORT).show()
            return
        }
        when (id) {
            R.id.cymbal -> {
                Log.e("say", "==================cymbal==============")
                temp = cymbal
                index = 0
            }
            R.id.drink -> {
                Log.e("say", "==================drink==============")
                temp = drink
                index = 4
            }
            R.id.eat -> {
                Log.e("say", "==================eat==============")
                temp = eat
                index = 5
            }
            R.id.fart -> {
                Log.e("say", "==================fart==============")
                temp = fart
                index = 3
            }
            R.id.pie -> {
                Log.e("say", "==================pie==============")
                temp = pie
                index = 2
            }
            R.id.scratch -> {
                Log.e("say", "==================scratch==============")
                temp = scratch
                index = 1
            }
            else -> return
        }
        startAnimation(temp, holder)
    }

    private fun reAction(actionIndex:Int){
        if (isPlaying) {
            return
        }
        var express = false
        index = actionIndex
        if(index > 6){
            express = true
            index -= 6
        }
        when(index){
            0 -> {
                temp = cymbal
            }
            1 -> {
                temp = scratch
            }
            2 -> {
                temp = pie
            }
            3 -> {
                temp = fart
            }
            4 -> {
                temp = drink
            }
            5 -> {
                temp = eat
            }
            else -> return
        }
        startAnimation(temp, holder, expression=express)
    }

    /**
     * 点击事件
     * 规定：
     * 修饰符 必须 public
     * 返回值 必须 void
     * 方法名称 大小写区分 xml文件必须一致
     * 参数 必须是一个View 只能有一个view
     * @param v 被点击的view
     */
    fun click(v: View) {
        //获取被点击view的id
        val id = v.id
        return catPlay(id)
    }

    /**
     * 逐帧动画
     */
    private fun startAnimation(temp: Array<Any>?, holder: SurfaceHolder?, expression: Boolean=false) {
        //一个新线程
        object : Thread() {
            override fun run() {
                isPlaying = true
                if(!expression) {
                    // Timer().schedule 会报错
                    Executors.newSingleThreadScheduledExecutor().schedule({
                        //播放声音
                        val id = soundIds!![index]
                        //播放声音 ，
                        // 参数1 是 声音池的id，参数2左声道，参数3右声道，参数5是循环参数
                        //参数 4 播放优先级，参数6比特率
                        soundPool!!.play(id, 1f, 1f, 1, 0, 1f)
                    }, delay[index].toLong(), TimeUnit.MILLISECONDS)
                }
                //获取应用包名
                val pgkName = packageName
                var playNum = temp!![1] as Int
                if(expression){
                    playNum = 4
                }
                for (i in 0 until playNum) {
                    //资源名
                    val name = if (i < 10) temp[0].toString() + 0 + i else temp[0].toString() + i
                    //获取资源id 参数1 资源名(无后缀)，参数2 哪个文件夹，参数3 包名
                    val rId = resources!!.getIdentifier(name, "mipmap", pgkName)
                    drawBitmap(rId, holder)
                    //睡眠60ms
                    try {
                        sleep(60)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                if(expression){
                    for (j in 0 until playNum) {
                        val i = playNum - j
                        val name = if (i < 10) temp[0].toString() + 0 + i else temp[0].toString() + i
                        //获取资源id 参数1 资源名(无后缀)，参数2 哪个文件夹，参数3 包名
                        val rId = resources!!.getIdentifier(name, "mipmap", pgkName)
                        drawBitmap(rId, holder)
                        //睡眠60ms
                        try {
                            sleep(60)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                }
                isPlaying = false
            }
        }.start()
    }

    //统计触发几次
    private var count = 0

    /**
     * 屏幕触摸事件
     * @param event 一次触摸事件
     * @return
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 触摸:按下 移动 ，抬起
        if (isPlaying) {
            Toast.makeText(this@MainActivity, "wait a little", Toast.LENGTH_SHORT)
        } else {
            //event.getAction() 获取事件的类型
            if (event.action == MotionEvent.ACTION_DOWN) {
                //按下
                count++
                if (count == 5) {
                    count = 0
                    index = 6 //改变下标
                    startAnimation(knockout, holder)
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
