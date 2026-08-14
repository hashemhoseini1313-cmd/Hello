[app]

title = ??? ????
package.name = screenrecorder
package.domain = org.example

source.dir = .
source.include_exts = py,java,png,jpg,kv

version = 1.0

requirements = python3,kivy,pyjnius

orientation = portrait

fullscreen = 0

android.api = 35
android.minapi = 23
android.ndk = 25b

android.permissions = INTERNET,FOREGROUND_SERVICE,FOREGROUND_SERVICE_MEDIA_PROJECTION

android.archs = arm64-v8a,armeabi-v7a

android.accept_sdk_license = True

[buildozer]

log_level = 2
warn_on_root = 1