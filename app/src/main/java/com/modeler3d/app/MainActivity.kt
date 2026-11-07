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
    private lateinit var keyInput: EditText
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

        val keyLabel = TextView(this).apply {
            text = "کلید API سایت Tripo"
            textSize = 16f
            typeface = font
        }

        keyInput = EditText(this).apply {
            hint = "کلید را اینجا بچسبانید"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            typeface = font
            setSingleLine(true)
        }

        val saveBtn = Button(this).apply {
            text = "ذخیره کلید"
            typeface = font
            setOnClickListener { saveKey() }
        }

        val testBtn = Button(this).apply {
            text = "تست کلید (بدون مصرف اعتبار)"
            typeface = font
            setOnClickListener { testKey() }
        }

        val deleteBtn = Button(this).apply {
            text = "حذف کلید"
            typeface = font
            setOnClickListener {
                store.clearTripoKey()
                refreshStatus()
            }
        }

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = font
            setPadding(0, dp(8), 0, dp(24))
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
        content.addView(keyLabel)
        content.addView(keyInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(saveBtn)
        content.addView(testBtn)
        content.addView(deleteBtn)
        content.addView(status)
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

    private fun saveKey() {
        val raw = keyInput.text.toString()
        // فقط حروف انگلیسی، عدد و علامت‌های معمولی؛ کاراکترهای نامرئی حذف می‌شوند
        val k = raw.filter { it.code in 33..126 }
        if (k.isEmpty()) {
            status.text = "ابتدا کلید را وارد کنید"
            return
        }
        try {
            store.saveTripoKey(k)
            keyInput.setText("")
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(keyInput.windowToken, 0)
            refreshStatus()
            if (k.length != raw.length) {
                progressText.text = "توجه: " + (raw.length - k.length) +
                        " کاراکتر اضافه یا نامرئی از کلید حذف شد."
            }
        } catch (e: Exception) {
            status.text = "خطا در ذخیره: " + e.message
        }
    }

    private fun refreshStatus() {
        val k = store.loadTripoKey()
        status.text = if (k == null) {
            "هنوز کلیدی ذخیره نشده است"
        } else {
            "کلید ذخیره شده ✅\nشروع: " + k.take(4) + "  پایان: " + k.takeLast(4) +
                    "  طول: " + k.length
        }
    }

    private fun testKey() {
        val key = store.loadTripoKey()
        if (key == null) {
            progressText.text = "ابتدا کلید Tripo را ذخیره کنید"
            return
        }
        progressText.text = "در حال تست کلید…"
        Thread {
            val result = TripoClient(key).diagnose()
            runOnUiThread { progressText.text = result }
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
        val key = store.loadTripoKey()
        if (key == null) {
            progressText.text = "ابتدا کلید Tripo را ذخیره کنید"
            return
        }

        busy = true
        startBtn.isEnabled = false
        resultText.text = ""

        Thread {
            try {
                postProgress("در حال آماده‌سازی عکس…")
                val jpeg = ImageUtil.prepareJpeg(this, uri)

                val client = TripoClient(key)
                postProgress("در حال آپلود عکس…")
                val token = client.uploadImage(jpeg)

                postProgress("در حال ساخت وظیفه در Tripo…")
                val taskId = client.createTask(token)

                val modelUrl = client.waitForModelUrl(taskId) { st, p ->
                    postProgress("در حال ساخت مدل… $p% ($st)")
                }

                postProgress("در حال دانلود مدل…")
                val dir = File(filesDir, "models").apply { mkdirs() }
                val file = File(dir, "model_" + System.currentTimeMillis() + ".glb")
                client.download(modelUrl, file)

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
