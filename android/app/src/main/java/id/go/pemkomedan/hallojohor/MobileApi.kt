package id.go.pemkomedan.hallojohor

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ApiException(message: String) : IOException(message)

/**
 * HTTP client kecil tanpa service-role key atau dependency pihak ketiga.
 * Semua panggilan dijalankan ViewModel pada Dispatchers.IO.
 */
class MobileApi(baseUrl: String) {
    private val baseUrl = baseUrl.trimEnd('/')

    private fun request(path: String, method: String = "GET", payload: JSONObject? = null): JSONObject {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-App-Version", "1.0.0")
            if (payload != null) {
                doOutput = true
                outputStream.use { stream ->
                    stream.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (code !in 200..299 || response.optBoolean("ok", true).not()) {
                throw ApiException(response.optString("error", "Server tidak dapat memproses permintaan."))
            }
            response
        } catch (error: ApiException) {
            throw error
        } catch (error: Exception) {
            throw ApiException(error.message ?: "Tidak dapat terhubung ke server.")
        } finally {
            connection.disconnect()
        }
    }

    fun loadBootstrap(): BootstrapData {
        val root = request("/api/mobile/bootstrap")
        return BootstrapData(
            services = root.array("services") { item ->
                ServiceItem(item.string("id"), item.string("title"), item.string("icon"), item.string("content"))
            },
            requirements = root.array("requirements") { item ->
                RequirementItem(item.string("code"), item.string("title"), item.string("content"))
            },
            wisata = root.array("wisata") { item ->
                TouristItem(item.string("code"), item.string("title"), item.string("content"))
            },
            categories = root.array("categories") { item ->
                CategoryItem(item.string("id"), item.string("label"), item.string("emoji"))
            },
            wards = root.array("kelurahan") { item ->
                WardItem(item.string("id"), item.string("label"))
            },
            activities = root.array("kegiatan") { item ->
                ActivityItem(item.string("id"), item.string("nama"), item.string("deskripsi"), item.string("tempat"), item.string("tanggal"))
            },
            umkm = root.array("umkm") { item ->
                UmkmItem(item.string("id"), item.string("nama"), item.string("kategori"), item.string("alamat"), item.string("mapsUrl"), item.string("kontak"))
            },
            ivaQuestions = root.array("ivaQuestions") { item ->
                IvaQuestion(
                    item.string("id"),
                    item.string("text"),
                    item.array("options") { option -> IvaOption(option.string("value"), option.string("label")) },
                )
            },
            contact = root.obj("contact")?.let { item ->
                ContactInfo(
                    item.string("office"), item.string("address"), item.string("phone"),
                    item.string("email"), item.string("mapsUrl"), item.string("serviceHours"),
                )
            } ?: ContactInfo("Kantor Kecamatan Medan Johor", "", "0813-6777-2047", "", "", ""),
        )
    }

    fun trackUsage(clientId: String, feature: String, action: String = "view") {
        request("/api/mobile/usage", "POST", JSONObject().apply {
            put("clientId", clientId)
            put("feature", feature)
            put("action", action)
        })
    }

    fun getReports(clientId: String): List<ReportItem> {
        val root = request("/api/mobile/reports?clientId=${encode(clientId)}")
        return root.array("reports") { parseReport(it) }
    }

    fun submitReport(
        clientId: String,
        name: String,
        contact: String,
        categoryId: String,
        wardId: String,
        description: String,
        latitude: Double,
        longitude: Double,
        photoBase64: String?,
        photoMime: String?,
    ): ReportItem {
        val root = request("/api/mobile/reports", "POST", JSONObject().apply {
            put("clientId", clientId)
            put("name", name)
            put("contact", contact)
            put("categoryId", categoryId)
            put("wardId", wardId)
            put("description", description)
            put("latitude", latitude)
            put("longitude", longitude)
            if (!photoBase64.isNullOrBlank()) {
                put("photoBase64", photoBase64)
                put("photoMime", photoMime ?: "image/jpeg")
            }
        })
        return parseReport(root.obj("report") ?: throw ApiException("Respons laporan tidak lengkap."))
    }

    fun submitIva(clientId: String, name: String, answers: Map<String, String>): IvaResult {
        val root = request("/api/mobile/iva", "POST", JSONObject().apply {
            put("clientId", clientId)
            put("name", name)
            put("answers", JSONObject().apply { answers.forEach { (key, value) -> put(key, value) } })
        })
        return IvaResult(root.optInt("score"), root.string("risk"))
    }

    fun getChat(clientId: String): ChatSession? {
        val root = request("/api/mobile/livechat?clientId=${encode(clientId)}")
        return root.obj("session")?.let(::parseChat)
    }

    fun startChat(clientId: String, name: String): ChatSession {
        val root = request("/api/mobile/livechat/start", "POST", JSONObject().apply {
            put("clientId", clientId)
            put("name", name)
        })
        return parseChat(root.obj("session") ?: throw ApiException("Sesi chat tidak tersedia."))
    }

    fun sendChat(clientId: String, message: String): ChatSession {
        val root = request("/api/mobile/livechat/message", "POST", JSONObject().apply {
            put("clientId", clientId)
            put("message", message)
        })
        return parseChat(root.obj("session") ?: throw ApiException("Sesi chat tidak tersedia."))
    }

    fun closeChat(clientId: String) {
        request("/api/mobile/livechat/close", "POST", JSONObject().put("clientId", clientId))
    }

    private fun parseReport(item: JSONObject) = ReportItem(
        id = item.string("id"),
        name = item.string("namaPelapor"),
        contact = item.string("kontak"),
        category = item.string("kategori"),
        ward = item.string("kelurahan"),
        description = item.string("isi"),
        address = item.string("alamat"),
        status = item.string("status"),
        date = item.string("tanggal"),
    )

    private fun parseChat(item: JSONObject): ChatSession {
        return ChatSession(
            id = item.string("id"),
            name = item.string("name"),
            status = item.string("status"),
            messages = item.array("messages") { message ->
                ChatMessage(message.string("id"), message.string("from"), message.string("text"), message.string("timestamp"))
            },
        )
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun JSONObject.string(key: String): String = optString(key, "")
    private fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)
    private inline fun <T> JSONObject.array(key: String, mapper: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(mapper(it)) }
            }
        }
    }
}
