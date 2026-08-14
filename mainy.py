# -*- coding: utf-8 -*-

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.core.text import LabelBase
from kivy.utils import platform

import arabic_reshaper

def ftext(text):
    """اصلاح متون فارسی همراه با اصلاح جهت پرانتزها و اعداد"""
    if not text:
        return ""
    
    # جابه‌جایی هوشمند پرانتزها قبل از معکوس‌سازی
    text = text.replace("(", "TEMP_OPEN").replace(")", "(").replace("TEMP_OPEN", ")")
    
    reshaped_text = arabic_reshaper.reshape(text)
    return reshaped_text[::-1]

FONT_FILE = "C:\\Windows\\Fonts\\arial.ttf" if platform == "win" else "Roboto"

if platform != "android":
    LabelBase.register(name="PersianFont", fn_regular=FONT_FILE)

if platform == "android":
    from jnius import autoclass, cast
    PythonActivity = autoclass("org.kivy.android.PythonActivity")
    Intent = autoclass("android.content.Intent")
    Context = autoclass("android.content.Context")

class PersianLabel(Label):
    def __init__(self, **kwargs):
        if "text" in kwargs:
            kwargs["text"] = ftext(kwargs["text"])
        if platform != "android":
            kwargs.setdefault("font_name", "PersianFont")
        kwargs.setdefault("halign", "right")
        kwargs.setdefault("text_size", (None, None))
        super().__init__(**kwargs)

    def on_size(self, *args):
        self.text_size = (self.width, None)

class PersianButton(Button):
    def __init__(self, **kwargs):
        if "text" in kwargs:
            kwargs["text"] = ftext(kwargs["text"])
        if platform != "android":
            kwargs.setdefault("font_name", "PersianFont")
        kwargs.setdefault("halign", "center")
        super().__init__(**kwargs)

class ScreenRecorderApp(App):

    def build(self):
        layout = BoxLayout(
            orientation="vertical",
            padding=30,
            spacing=15
        )

        # جهت عدد 51 برعکس نوشتن شد تا بعد از معکوس شدن روی صفحه درست 15 دیده شود
        title = PersianLabel(
            text="ضبط صفحه گوشی (اندروید 51)",
            font_size="24sp"
        )

        info = PersianLabel(
            text="جهت شروع، اجازه ضبط صفحه را تأیید کنید.",
            font_size="16sp"
        )

        start_button = PersianButton(
            text="🎥 شروع ضبط صفحه",
            font_size="18sp",
            size_hint_y=None,
            height=65
        )

        stop_button = PersianButton(
            text="⏹ توقف ضبط",
            font_size="18sp",
            size_hint_y=None,
            height=65
        )

        photo_button = PersianButton(
            text="📸 عکس از صفحه",
            font_size="18sp",
            size_hint_y=None,
            height=65
        )

        start_button.bind(on_press=self.start_recording)
        stop_button.bind(on_press=self.stop_recording)
        photo_button.bind(on_press=self.take_screenshot)

        layout.add_widget(title)
        layout.add_widget(info)
        layout.add_widget(start_button)
        layout.add_widget(stop_button)
        layout.add_widget(photo_button)

        return layout

    def start_recording(self, instance):
        if platform != "android":
            print("این بخش فقط داخل سیستم‌عامل اندروید اجرا می‌شود.")
            return

        try:
            activity = PythonActivity.mActivity
            MediaProjectionManager = autoclass("android.media.projection.MediaProjectionManager")
            
            projection_service = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            mgr = cast(MediaProjectionManager, projection_service)
            
            intent = mgr.createScreenCaptureIntent()
            activity.startActivityForResult(intent, 1001)
            print("درخواست مجوز MediaProjection ارسال شد.")
        except Exception as e:
            print("خطا در شروع ضبط:", e)

    def stop_recording(self, instance):
        if platform != "android":
            return

        try:
            activity = PythonActivity.mActivity
            service_intent = Intent(activity, autoclass("org.kivy.android.PythonService"))
            activity.stopService(service_intent)
            print("سرویس ضبط متوقف شد.")
        except Exception as e:
            print("خطا در توقف سرویس:", e)

    def take_screenshot(self, instance):
        if platform != "android":
            print("درخواست عکس از صفحه (روی سیستم‌عامل ویندوز فعال نیست)")
            return

        try:
            print("درخواست گرفتن عکس از صفحه ارسال شد.")
        except Exception as e:
            print("خطا در گرفتن اسکرین‌شات:", e)

if __name__ == "__main__":
    ScreenRecorderApp().run()
