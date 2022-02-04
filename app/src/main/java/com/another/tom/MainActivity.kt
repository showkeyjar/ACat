package com.another.tom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.another.tom.databinding.ActivityMainBinding
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

typealias LumaListener = (luma: Double) -> Unit


class MainActivity : AppCompatActivity(){
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
    var temp: Array<Any>? = null
    //声音池 短声音播放
    private var soundPool: SoundPool? = null
    //是否正在播放动画
    private var isPlaying = false
    //声音池id集合
    private var soundIds: ArrayList<Int>? = ArrayList<Int>()
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

    /*
    https://developer.android.com/codelabs/camerax-getting-started?hl=zh-cn#5
    https://developer.android.com/training/camerax/analyze
    目前 CameraX 不支持直接对video进行读取分析，只能先使用 ImageAnalysis
    */
    private lateinit var viewBinding: ActivityMainBinding
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

        private fun byteArrayToString(inputFace: ByteArray):String{
            val encodedString: String = Base64.encodeToString(inputFace, Base64.DEFAULT)
            return encodedString
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
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            // 将图像数据发送给python
            var encodingStr = byteArrayToString(data)
            Log.i("input", encodingStr.slice(1..50))
            try {
                react = py.getModule("cat").callAttr("see", encodingStr).toInt()
            }catch (e: Exception) {
                Log.i("error", e.toString())
            }
            // val pixels = data.map { it.toInt() and 0xFF }
            // val luma = pixels.average()
            listener(react.toDouble())
            image.close()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer({ luma ->
                        Log.d(TAG, "Average luminosity: $luma")
                    }, applicationContext))
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
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
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        //加载布局文件 设置显示的内容
        // setContentView(R.layout.activity_main)
        setContentView(viewBinding.root)

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

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
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
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
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

            drawBitmap(R.mipmap.cymbal_12)
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
     * 绘制图`片
     *
     */
    private fun drawBitmap(id: Int) {
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
        if (isPlaying) {
            Toast.makeText(this@MainActivity, "再快点屏就戳烂了，慢点戳！", Toast.LENGTH_SHORT).show()
            return
        }
        //获取被点击view的id
        val id = v.id
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
        startAnimation(temp)
    }

    private fun startAnimation(temp: Array<Any>?) {
        //一个新线程
        object : Thread() {
            override fun run() {
                isPlaying = true
                // Timer().schedule 会报错
                Executors.newSingleThreadScheduledExecutor().schedule({
                    //播放声音
                    val id = soundIds!![index]
                    //播放声音 ，
                    // 参数1 是 声音池的id，参数2左声道，参数3右声道，参数5是循环参数
                    //参数 4 播放优先级，参数6比特率
                    soundPool!!.play(id, 1f, 1f, 1, 0, 1f)
                }, delay[index].toLong(), TimeUnit.MILLISECONDS)

                //获取应用包名
                val pgkName = packageName
                for (i in 0 until temp!![1] as Int) {

                    //资源名
                    val name = if (i < 10) temp[0].toString() + 0 + i else temp[0].toString() + i

                    //获取资源id 参数1 资源名(无后缀)，参数2 哪个文件夹，参数3 包名
                    val rId = resources!!.getIdentifier(name, "mipmap", pgkName)
                    drawBitmap(rId)

                    //睡眠60ms
                    try {
                        sleep(60)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                isPlaying = false
            }
        }.start()
    }

    //统计触发几次
    var count = 0

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
                    startAnimation(knockout)
                }
            }
        }
        return super.onTouchEvent(event)
    }
}

