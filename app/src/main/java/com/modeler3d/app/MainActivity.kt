package com.modeler3d.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {

    private lateinit var store: SecureStore
    private lateinit var status: TextView
    private lateinit var tokenInput: EditText
    private lateinit var spaceInput: EditText
    private lateinit var preview: ImageView
    private lateinit var startBtn: Button
    private lateinit var progressText: TextView
    private lateinit var resultText: TextView
    private var font: Typeface? = null
    private var selectedUri: Uri? = null
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
            text = "انتخاب عکس"
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
        setContentView(scroll)

        refreshStatus()
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
        }
        startActivityForResult(Intent.createChooser(intent, "انتخاب عکس"), pickRequest)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            preview.setImageBitmap(ImageUtil.thumbnail(this, uri))
            preview.visibility = View.VISIBLE
            selectedUri = uri
            startBtn.isEnabled = !busy
            progressText.text = "عکس انتخاب شد. برای شروع، دکمه‌ی تبدیل را بزنید."
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
        val uri = selectedUri
        if (uri == null) {
            progressText.text = "ابتدا یک عکس انتخاب کنید"
            return
        }
        val space = currentSpace()
        val token = store.load("hf")

        busy = true
        startBtn.isEnabled = false
        resultText.text = ""

        Thread {
            try {
                postProgress("در حال آماده‌سازی عکس…")
                val jpeg = ImageUtil.prepareJpeg(this, uri)

                val client = HfClient(space, token)
                postProgress("در حال اتصال به Space…")
                val info = client.fetchInfo()
                val preName = client.findEndpoint(info, "/preprocess_image")
                val genName = client.findEndpoint(info, "/generate_and_extract_glb")

                postProgress("در حال آپلود عکس…")
                val uploaded = client.upload(jpeg, "image.jpg", "image/jpeg")

                val t0 = System.currentTimeMillis()
                postProgress("در حال حذف پس‌زمینه…")
                val preResult = client.callEndpoint(
                    preName, client.buildArgs(info, preName, uploaded)
                ) {
                    val sec = (System.currentTimeMillis() - t0) / 1000
                    postProgress("در حال حذف پس‌زمینه… ($sec ثانیه)")
                }
                val processed = client.firstFile(preResult)
                    ?: throw HfClient.HfException("خروجی حذف پس‌زمینه فایل نداشت: " + preResult.toString().take(200))

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
