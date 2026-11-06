package com.modeler3d.app

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        }
        val status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        try {
            val font = Typeface.createFromAsset(assets, "fonts/Vazirmatn_Light.ttf")
            title.typeface = font
            status.typeface = font
            status.text = "فونت وزیرمتن با موفقیت بارگذاری شد ✅"
        } catch (e: Exception) {
            status.text = "فونت پیدا نشد. مسیر فایل را بررسی کنید."
        }

        root.addView(title)
        root.addView(status)
        setContentView(root)
    }
}
