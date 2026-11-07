package com.modeler3d.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.random.Random

class HfClient(spaceInput: String, private val token: String?) {

    class HfException(message: String) : Exception(message)

    companion object {
        const val DEFAULT_SPACE = "Stable-X/ReconViaGen"

        fun normalizeHost(input: String): String {
            var s = input.trim().trimEnd('/')
            if (s.contains("huggingface.co/spaces/")) {
                s = s.substringAfter("huggingface.co/spaces/").trimEnd('/')
            } else if (s.startsWith("http://") || s.startsWith("https://")) {
                return s
            }
            val slug = s.lowercase().replace("/", "-").replace("_", "-").replace(".", "-")
            return "https://$slug.hf.space"
        }
    }

    private val host = normalizeHost(spaceInput)
    private val sessionHash = UUID.randomUUID().toString().replace("-", "").take(11)

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 30000
        conn.readTimeout = 180000
        if (!token.isNullOrEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    fun fetchInfo(): JSONObject {
        val conn = open("$host/gradio_api/info", "GET")
        val code = conn.responseCode
        val body = readBody(conn)
        if (code !in 200..299) {
            throw HfException("اتصال به Space ناموفق ($code): " + body.take(300))
        }
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw HfException(
                "پاسخ Space قابل خواندن نبود (شاید در حال بیدار شدن است؛ چند دقیقه بعد دوباره تلاش کنید): " +
                        body.take(200)
            )
        }
    }

    fun describe(info: JSONObject): String {
        val eps = info.optJSONObject("named_endpoints") ?: return "لیست endpoint پیدا نشد"
        val sb = StringBuilder()
        val keys = eps.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val params = eps.getJSONObject(name).optJSONArray("parameters")
            val names = ArrayList<String>()
            if (params != null) {
                for (i in 0 until params.length()) {
                    names.add(params.getJSONObject(i).optString("parameter_name", "?"))
                }
            }
            sb.append(name).append(" (").append(names.joinToString(", ")).append(")\n")
        }
        return sb.toString()
    }

    fun findEndpointOrNull(info: JSONObject, exact: String): String? {
        val eps = info.optJSONObject("named_endpoints") ?: return null
        if (eps.has(exact)) return exact
        val names = ArrayList<String>()
        val keys = eps.keys()
        while (keys.hasNext()) names.add(keys.next())
        val re = Regex(Regex.escape(exact) + "(_\\d+)?")
        return names.firstOrNull { re.matches(it) }
    }

    fun findEndpoint(info: JSONObject, exact: String): String {
        val eps = info.optJSONObject("named_endpoints")
            ?: throw HfException("لیست endpointها در مشخصات Space نبود")
        findEndpointOrNull(info, exact)?.let { return it }
        val names = ArrayList<String>()
        val keys = eps.keys()
        while (keys.hasNext()) names.add(keys.next())
        throw HfException(
            "endpoint «$exact» در این Space پیدا نشد. موجود: " + names.joinToString(", ")
        )
    }

    fun upload(bytes: ByteArray, filename: String, mime: String): JSONObject {
        val boundary = "----Modeler" + System.currentTimeMillis()
        val head = ("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"\r\n" +
                "Content-Type: " + mime + "\r\n\r\n").toByteArray()
        val tail = ("\r\n--" + boundary + "--\r\n").toByteArray()

        val conn = open("$host/gradio_api/upload", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.setFixedLengthStreamingMode(head.size + bytes.size + tail.size)
        conn.outputStream.use { out ->
            out.write(head)
            out.write(bytes)
            out.write(tail)
        }
        val code = conn.responseCode
        val body = readBody(conn)
        if (code !in 200..299) {
            throw HfException("آپلود عکس ناموفق ($code): " + body.take(300))
        }
        val path = try {
            JSONArray(body).getString(0)
        } catch (e: Exception) {
            throw HfException("پاسخ آپلود نامعتبر: " + body.take(200))
        }
        return JSONObject()
            .put("path", path)
            .put("orig_name", filename)
            .put("meta", JSONObject().put("_type", "gradio.FileData"))
    }

    private fun galleryOf(files: List<JSONObject>): JSONArray {
        val arr = JSONArray()
        for (f in files) {
            arr.put(JSONObject().put("image", f).put("caption", JSONObject.NULL))
        }
        return arr
    }

    fun buildArgs(info: JSONObject, endpoint: String, files: List<JSONObject>): JSONArray {
        val ep = info.getJSONObject("named_endpoints").getJSONObject(endpoint)
        val params = ep.getJSONArray("parameters")
        val out = JSONArray()
        for (i in 0 until params.length()) {
            val p = params.getJSONObject(i)
            val pname = p.optString("parameter_name", "")
            val comp = p.optString("component", "")
            val hasDefault = p.optBoolean("parameter_has_default", false)
            val def: Any? = if (hasDefault) p.opt("parameter_default") else null
            val usableDefault = def != null && def != JSONObject.NULL
            val known: Any? = when (pname) {
                "ss_guidance_strength" -> 7.5
                "ss_sampling_steps" -> 12
                "slat_guidance_strength" -> 3.0
                "slat_sampling_steps" -> 12
                "multiimage_algo" -> "stochastic"
                "mesh_simplify" -> 0.95
                "is_multiimage" -> false
                else -> null
            }
            when {
                pname == "seed" -> out.put(Random.nextInt(0, 2000000000))
                pname == "texture_size" -> out.put(2048)
                comp == "Gallery" || pname == "multiimages" || pname == "images" ->
                    out.put(galleryOf(files))
                comp == "Image" || pname == "image" -> out.put(files.first())
                usableDefault -> out.put(def)
                known != null -> out.put(known)
                else -> out.put(JSONObject.NULL)
            }
        }
        return out
    }

    fun callEndpoint(apiName: String, data: JSONArray, onEvent: (String) -> Unit): JSONArray {
        val name = apiName.trimStart('/')
        val payload = JSONObject()
            .put("data", data)
            .put("session_hash", sessionHash)
            .toString().toByteArray()

        val post = open("$host/gradio_api/call/$name", "POST")
        post.doOutput = true
        post.setRequestProperty("Content-Type", "application/json")
        post.setFixedLengthStreamingMode(payload.size)
        post.outputStream.use { it.write(payload) }
        val postCode = post.responseCode
        val postBody = readBody(post)
        if (postCode !in 200..299) {
            throw HfException("فراخوانی $name ناموفق ($postCode): " + postBody.take(300))
        }
        val eventId = try {
            JSONObject(postBody).optString("event_id", "")
        } catch (e: Exception) {
            ""
        }
        if (eventId.isEmpty()) {
            throw HfException("شناسه‌ی رویداد دریافت نشد: " + postBody.take(200))
        }

        val get = open("$host/gradio_api/call/$name/$eventId", "GET")
        val getCode = get.responseCode
        if (getCode !in 200..299) {
            throw HfException("دریافت نتیجه ناموفق ($getCode): " + readBody(get).take(300))
        }
        get.inputStream.bufferedReader().use { reader ->
            var event = ""
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("event:")) {
                    event = line.substring(6).trim()
                } else if (line.startsWith("data:")) {
                    val payloadText = line.substring(5).trim()
                    when (event) {
                        "complete" -> return JSONArray(payloadText)
                        "error" -> throw HfException("خطای Space: $payloadText")
                        else -> onEvent(event)
                    }
                }
            }
        }
        throw HfException("ارتباط با Space بدون دریافت نتیجه بسته شد")
    }

    fun firstFile(result: JSONArray): JSONObject? {
        for (i in 0 until result.length()) {
            val o = result.optJSONObject(i) ?: continue
            if (o.has("path") || o.has("url")) return o
        }
        return null
    }

    fun collectFiles(node: Any?): List<JSONObject> {
        val found = ArrayList<JSONObject>()
        collectFilesInto(node, found)
        return found
    }

    private fun collectFilesInto(node: Any?, found: MutableList<JSONObject>) {
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) collectFilesInto(node.opt(i), found)
            }
            is JSONObject -> {
                if (node.optString("path", "").isNotEmpty()) {
                    found.add(node)
                } else {
                    val keys = node.keys()
                    while (keys.hasNext()) collectFilesInto(node.opt(keys.next()), found)
                }
            }
        }
    }

    fun findGlbUrl(node: Any?): String? {
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findGlbUrl(node.opt(i))?.let { return it }
                }
            }
            is JSONObject -> {
                val url = node.optString("url", "")
                val path = node.optString("path", "")
                if (url.endsWith(".glb", true)) return url
                if (path.endsWith(".glb", true)) return "$host/gradio_api/file=$path"
                val keys = node.keys()
                while (keys.hasNext()) {
                    findGlbUrl(node.opt(keys.next()))?.let { return it }
                }
            }
        }
        return null
    }

    fun download(url: String, dest: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        if (url.startsWith(host) && !token.isNullOrEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        if (conn.responseCode !in 200..299) {
            throw HfException("دانلود مدل ناموفق (" + conn.responseCode + ")")
        }
        conn.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        val magic = dest.inputStream().use { s ->
            val b = ByteArray(4)
            val n = s.read(b)
            if (n == 4) String(b, Charsets.US_ASCII) else ""
        }
        if (magic != "glTF") {
            dest.delete()
            throw HfException("فایل دانلودشده GLB معتبر نبود")
        }
    }
}
