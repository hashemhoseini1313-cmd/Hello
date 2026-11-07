package com.modeler3d.app

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    private lateinit var store: SecureStore
    private lateinit var status: TextView
    private lateinit var tokenInput: EditText
    private lateinit var spaceInput: EditText
    private lateinit var preview: ImageView
    private lateinit var startBtn: Button
    private lateinit var progressText: TextView
    private lateinit var resultText: TextView
    private lateinit var viewerView: ComposeView
    private val modelState = mutableStateOf<File?>(null)
    private var font: Typeface? = null
    private var selectedUris: List<Uri> = emptyList()
    private var busy = false
    private val pickRequest = 101

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        font = try {
            Typeface.createFromAsset(assets, "fonts/Vazirmatn_Light.ttf")
        } catch (e: Exception) {
            null
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(content)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            content.setPadding(dp(16), bars.top + dp(16), dp(16), bars.bottom + dp(16))
            insets
        }

        val title = TextView(this).apply {
            text = "طراح سه‌بعدی هوشمند"
            textSize = 26f
            gravity = Gravity.CENTER
            typeface = font
            setPadding(0, dp(16), 0, dp(16))
        }

        val tokenLabel = TextView(this).apply {
            text = "توکن Hugging Face (اختیاری، سهمیه‌ی رایگان را بیشتر می‌کند)"
            textSize = 16f
            typeface = font
        }

        tokenInput = EditText(this).apply {
            hint = "توکن را اینجا بچسبانید"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            typeface = font
            setSingleLine(true)
        }

        val saveBtn = Button(this).apply {
            text = "ذخیره توکن"
            typeface = font
            setOnClickListener { saveToken() }
        }

        val deleteBtn = Button(this).apply {
            text = "حذف توکن"
            typeface = font
            setOnClickListener {
                store.clear("hf")
                refreshStatus()
            }
        }

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = font
            setPadding(0, dp(8), 0, dp(16))
        }

        val spaceLabel = TextView(this).apply {
            text = "آدرس Space (اگر کار نکرد، Space دیگری را جایگزین کنید)"
            textSize = 16f
            typeface = font
        }

        val savedSpace = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("space", HfClient.DEFAULT_SPACE) ?: HfClient.DEFAULT_SPACE
        spaceInput = EditText(this).apply {
            setText(savedSpace)
            inputType = InputType.TYPE_CLASS_TEXT
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            typeface = font
            setSingleLine(true)
        }

        val testBtn = Button(this).apply {
            text = "تست اتصال به Space"
            typeface = font
            setOnClickListener { testConnection() }
        }

        val pickBtn = Button(this).apply {
            text = "انتخاب عکس‌ها (چندتایی)"
            typeface = font
            setOnClickListener { pickImage() }
        }

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }

        startBtn = Button(this).apply {
            text = "شروع تبدیل به سه‌بعدی"
            typeface = font
            isEnabled = false
            setOnClickListener { startConversion() }
        }

        progressText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = font
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        }

        resultText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = font
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, 0)
        }

        viewerView = ComposeView(this).apply {
            visibility = View.GONE
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            setContent {
                val file = modelState.value
                if (file != null) {
                    ModelViewer(file) { msg -> postProgress(msg) }
                }
            }
        }

        content.addView(title)
        content.addView(tokenLabel)
        content.addView(tokenInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(saveBtn)
        content.addView(deleteBtn)
        content.addView(status)
        content.addView(spaceLabel)
        content.addView(spaceInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(testBtn)
        content.addView(pickBtn)
        content.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
        ))
        content.addView(startBtn)
        content.addView(progressText)
        content.addView(resultText)
        content.addView(viewerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(380)
        ))
        setContentView(scroll)

        refreshStatus()
        loadLastModel()
    }

    private fun loadLastModel() {
        val dir = File(filesDir, "models")
        val last = dir.listFiles { f -> f.name.endsWith(".glb") }
            ?.maxByOrNull { it.lastModified() }
        if (last != null) showModel(last)
    }

    private fun showModel(file: File) {
        modelState.value = file
        viewerView.visibility = View.VISIBLE
    }

    private fun saveToken() {
        val raw = tokenInput.text.toString()
        val k = raw.filter { it.code in 33..126 }
        if (k.isEmpty()) {
            status.text = "ابتدا توکن را وارد کنید"
            return
        }
        try {
            store.save("hf", k)
            tokenInput.setText("")
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(tokenInput.windowToken, 0)
            refreshStatus()
        } catch (e: Exception) {
            status.text = "خطا در ذخیره: " + e.message
        }
    }

    private fun refreshStatus() {
        val k = store.load("hf")
        status.text = if (k == null) {
            "بدون توکن (سهمیه‌ی رایگان کمتر است)"
        } else {
            "توکن ذخیره شده ✅  (••••" + k.takeLast(4) + ")"
        }
    }

    private fun currentSpace(): String {
        val s = spaceInput.text.toString().trim()
        val space = if (s.isEmpty()) HfClient.DEFAULT_SPACE else s
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("space", space).apply()
        return space
    }

    private fun testConnection() {
        val space = currentSpace()
        val token = store.load("hf")
        progressText.text = "در حال اتصال به Space…"
        Thread {
            try {
                val client = HfClient(space, token)
                val info = client.fetchInfo()
                runOnUiThread {
                    progressText.text = "اتصال موفق ✅\n" + client.describe(info)
                }
            } catch (e: Exception) {
                runOnUiThread { progressText.text = "خطا: " + e.message }
            }
        }.start()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "انتخاب عکس‌ها"), pickRequest)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickRequest || resultCode != RESULT_OK || data == null) return

        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris.add(it) }
            }
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return

        try {
            preview.setImageBitmap(ImageUtil.thumbnail(this, uris[0]))
            preview.visibility = View.VISIBLE
            selectedUris = uris
            startBtn.isEnabled = !busy
            progressText.text = uris.size.toString() +
                    " عکس انتخاب شد. برای شروع، دکمه‌ی تبدیل را بزنید."
            resultText.text = ""
        } catch (e: Exception) {
            progressText.text = "خطا در خواندن عکس: " + e.message
        }
    }

    private fun postProgress(msg: String) {
        runOnUiThread { progressText.text = msg }
    }

    private fun startConversion() {
        if (busy) return
        val uris = selectedUris.toList()
        if (uris.isEmpty()) {
            progressText.text = "ابتدا حداقل یک عکس انتخاب کنید"
            return
        }
        val space = currentSpace()
        val token = store.load("hf")

        busy = true
        startBtn.isEnabled = false
        resultText.text = ""

        Thread {
            try {
                val client = HfClient(space, token)
                postProgress("در حال اتصال به Space…")
                val info = client.fetchInfo()
                val preName = client.findEndpointOrNull(info, "/preprocess_images")
                    ?: client.findEndpoint(info, "/preprocess_image")
                val genName = client.findEndpoint(info, "/generate_and_extract_glb")

                val startName = client.findEndpointOrNull(info, "/start_session")
                if (startName != null) {
                    postProgress("در حال شروع نشست…")
                    try {
                        client.callEndpoint(startName, JSONArray()) { }
                    } catch (e: Exception) {
                        // اگر شروع نشست خطا داد، ادامه می‌دهیم
                    }
                }

                val uploaded = ArrayList<JSONObject>()
                for ((i, u) in uris.withIndex()) {
                    postProgress("در حال آماده‌سازی و آپلود عکس " + (i + 1) + " از " + uris.size + "…")
                    val jpeg = ImageUtil.prepareJpeg(this, u)
                    uploaded.add(client.upload(jpeg, "image_" + (i + 1) + ".jpg", "image/jpeg"))
                }

                val t0 = System.currentTimeMillis()
                postProgress("در حال حذف پس‌زمینه…")
                val preResult = client.callEndpoint(
                    preName, client.buildArgs(info, preName, uploaded)
                ) {
                    val sec = (System.currentTimeMillis() - t0) / 1000
                    postProgress("در حال حذف پس‌زمینه… ($sec ثانیه)")
                }
                val processed = client.collectFiles(preResult)
                if (processed.isEmpty()) {
                    throw HfClient.HfException("خروجی حذف پس‌زمینه فایل نداشت: " + preResult.toString().take(200))
                }

                val t1 = System.currentTimeMillis()
                val genResult = client.callEndpoint(
                    genName, client.buildArgs(info, genName, processed)
                ) {
                    val sec = (System.currentTimeMillis() - t1) / 1000
                    postProgress("در حال ساخت مدل روی سرور رایگان… ($sec ثانیه)\nصبور باشید، ممکن است چند دقیقه طول بکشد.")
                }
                val glbUrl = client.findGlbUrl(genResult)
                    ?: throw HfClient.HfException("فایل GLB در خروجی نبود: " + genResult.toString().take(300))

                postProgress("در حال دانلود مدل…")
                val dir = File(filesDir, "models").apply { mkdirs() }
                val file = File(dir, "model_" + System.currentTimeMillis() + ".glb")
                client.download(glbUrl, file)

                runOnUiThread {
                    progressText.text = "تمام شد ✅"
                    resultText.text = "فایل GLB ذخیره شد:\n" + file.name +
                            "\nحجم: " + (file.length() / 1024) + " کیلوبایت"
                    showModel(file)
                    busy = false
                    startBtn.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressText.text = "خطا: " + e.message
                    busy = false
                    startBtn.isEnabled = true
                }
            }
        }.start()
    }
}

@Composable
private fun ModelViewer(file: File, onError: (String) -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val nodes = rememberNodes()

    LaunchedEffect(file) {
        try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size)
            buffer.put(bytes)
            buffer.rewind()
            val instance = modelLoader.createModelInstance(buffer)
            nodes.clear()
            nodes.add(ModelNode(modelInstance = instance, scaleToUnits = 1.0f))
        } catch (e: Exception) {
            onError("خطا در نمایش مدل: " + e.message)
        }
    }

    Scene(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        cameraManipulator = rememberCameraManipulator(),
        childNodes = nodes
    )
}
