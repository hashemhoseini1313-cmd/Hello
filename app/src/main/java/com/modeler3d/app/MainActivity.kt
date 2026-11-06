package com.modeler3d.app

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var store: SecureStore
    private lateinit var status: TextView
    private lateinit var keyInput: EditText
    private var font: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        font = try {
            Typeface.createFromAsset(assets, "fonts/Vazirmatn_Light.ttf")
        } catch (e: Exception) {
            null
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(48, 48, 48, 48)
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(48, bars.top + 48, 48, bars.bottom + 48)
            insets
        }

        val title = TextView(this).apply {
            text = "طراح سه‌بعدی هوشمند"
            textSize = 28f
            gravity = Gravity.CENTER
            typeface = font
        }

        val label = TextView(this).apply {
            text = "کلید API سایت Tripo"
            textSize = 16f
            typeface = font
            setPadding(0, 64, 0, 8)
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
            setPadding(0, 32, 0, 0)
        }

        root.addView(title)
        root.addView(label)
        root.addView(keyInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(saveBtn)
        root.addView(deleteBtn)
        root.addView(status)
        setContentView(root)

        refreshStatus()
    }

    private fun saveKey() {
        val k = keyInput.text.toString().trim()
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
        } catch (e: Exception) {
            status.text = "خطا در ذخیره: " + e.message
        }
    }

    private fun refreshStatus() {
        val k = store.loadTripoKey()
        status.text = if (k == null) {
            "هنوز کلیدی ذخیره نشده است"
        } else {
            "کلید ذخیره شده ✅  (••••" + k.takeLast(4) + ")"
        }
    }
}
