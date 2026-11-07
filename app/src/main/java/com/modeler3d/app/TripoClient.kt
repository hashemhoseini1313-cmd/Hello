package com.modeler3d.app

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TripoClient(private val apiKey: String) {

    companion object {
        // برنامه به ترتیب هر دو سرور را امتحان می‌کند (کلید هر منطقه فقط روی سرور خودش کار می‌کند)
        val BASE_URLS = listOf(
            "https://openapi.tripo3d.ai/v3",
            "https://openapi.tripo3d.com/v3"
        )
        const val MODEL_VERSION = "v3.1-20260211"
    }

    class TripoException(message: String) : Exception(message)

    private var base = BASE_URLS[0]

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun dataOf(body: String): JSONObject {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw TripoException("پاسخ نامعتبر از سرور: $body")
        }
        if (json.optInt("code", 0) != 0) {
            throw TripoException("خطای Tripo: $body")
        }
        return json.optJSONObject("data") ?: throw TripoException("پاسخ بدون data: $body")
    }

    fun uploadImage(jpeg: ByteArray): String {
        var lastError = ""
        for (candidate in BASE_URLS) {
            val boundary = "----Modeler" + System.currentTimeMillis()
            val head = ("--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"\r\n" +
                    "Content-Type: image/jpeg\r\n\r\n").toByteArray()
            val tail = ("\r\n--" + boundary + "--\r\n").toByteArray()

            val conn = URL("$candidate/files").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 120000
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setFixedLengthStreamingMode(head.size + jpeg.size + tail.size)
            conn.outputStream.use { out ->
                out.write(head)
                out.write(jpeg)
                out.write(tail)
            }

            val code = conn.responseCode
            val body = readBody(conn)
            if (code == 401) {
                lastError = "($candidate) $body"
                continue
            }
            if (code !in 200..299) {
                throw TripoException("آپلود ناموفق ($code): $body")
            }
            val token = dataOf(body).optString("file_token", "")
            if (token.isEmpty()) throw TripoException("file_token دریافت نشد: $body")
            base = candidate
            return token
        }
        throw TripoException("کلید در هر دو سرور (.ai و .com) نامعتبر شناخته شد: $lastError")
    }

    fun createTask(fileToken: String): String {
        val payload = JSONObject()
            .put("input", fileToken)
            .put("model", MODEL_VERSION)
            .put("texture", true)
            .put("pbr", true)
        val bytes = payload.toString().toByteArray()

        val conn = URL("$base/generation/image-to-model").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }

        val body = readBody(conn)
        if (conn.responseCode !in 200..299) {
            throw TripoException("ساخت وظیفه ناموفق (" + conn.responseCode + "): " + body)
        }
        val taskId = dataOf(body).optString("task_id", "")
        if (taskId.isEmpty()) throw TripoException("task_id دریافت نشد: $body")
        return taskId
    }

    fun waitForModelUrl(taskId: String, onProgress: (String, Int) -> Unit): String {
        val deadline = System.currentTimeMillis() + 10 * 60 * 1000
        while (System.currentTimeMillis() < deadline) {
            val conn = URL("$base/tasks/$taskId").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer $apiKey")

            val body = readBody(conn)
            if (conn.responseCode !in 200..299) {
                throw TripoException("پیگیری وظیفه ناموفق (" + conn.responseCode + "): " + body)
            }
            val data = dataOf(body)
            val status = data.optString("status", "")
            val progress = data.optInt("progress", 0)
            onProgress(status, progress)

            when (status) {
                "success" -> {
                    val output = data.optJSONObject("output")
                    val url = if (output != null && !output.isNull("model_url")) {
                        output.optString("model_url", "")
                    } else ""
                    if (url.isEmpty()) throw TripoException("لینک مدل در پاسخ نبود: $body")
                    return url
                }
                "failed", "cancelled", "banned", "expired" ->
                    throw TripoException("ساخت مدل ناموفق بود. وضعیت: $status")
            }
            Thread.sleep(3000)
        }
        throw TripoException("زمان انتظار تمام شد")
    }

    fun download(url: String, dest: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        if (conn.responseCode !in 200..299) {
            throw TripoException("دانلود مدل ناموفق (" + conn.responseCode + ")")
        }
        conn.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
