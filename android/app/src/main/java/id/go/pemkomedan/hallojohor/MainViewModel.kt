package id.go.pemkomedan.hallojohor

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.go.pemkomedan.hallojohor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

private const val PREFS = "hallo_johor_app"
private const val CLIENT_ID = "client_id"

enum class DetailKind { STATIC, REQUIREMENTS, TOURISM, ACTIVITIES, UMKM }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = MobileApi(BuildConfig.API_BASE_URL)
    private val preferences = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val clientId: String = preferences.getString(CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString(CLIENT_ID, it).apply()
    }

    var bootstrap by mutableStateOf<BootstrapData?>(null)
        private set
    var screen by mutableStateOf(AppScreen.HOME)
        private set
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<UiMessage?>(null)
        private set
    var reports by mutableStateOf<List<ReportItem>>(emptyList())
        private set
    var selectedTitle by mutableStateOf("")
        private set
    var selectedContent by mutableStateOf("")
        private set
    var detailKind by mutableStateOf(DetailKind.STATIC)
        private set
    var selectedRequirement by mutableStateOf<RequirementItem?>(null)
        private set
    var selectedTourist by mutableStateOf<TouristItem?>(null)
        private set
    var chat by mutableStateOf<ChatSession?>(null)
        private set
    var ivaIndex by mutableStateOf(0)
        private set
    var ivaAnswers by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var ivaResult by mutableStateOf<IvaResult?>(null)
        private set

    private var chatPolling: Job? = null

    init {
        loadBootstrap()
    }

    fun dismissMessage() { message = null }

    fun goHome() {
        screen = AppScreen.HOME
        selectedRequirement = null
        selectedTourist = null
        chatPolling?.cancel()
    }

    fun loadBootstrap() {
        loading = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.loadBootstrap() }
                .onSuccess { data ->
                    bootstrap = data
                    loading = false
                    trackUsage("home", "view")
                }
                .onFailure { error ->
                    loading = false
                    message = UiMessage(error.message ?: "Gagal memuat layanan.", true)
                }
        }
    }

    fun openService(service: ServiceItem) {
        selectedTitle = service.title
        selectedContent = service.content
        detailKind = when (service.id) {
            "persyaratan" -> DetailKind.REQUIREMENTS
            "wisata" -> DetailKind.TOURISM
            else -> DetailKind.STATIC
        }
        selectedRequirement = null
        selectedTourist = null
        screen = AppScreen.DETAIL
        trackUsage(service.id)
    }

    fun openActivities() {
        selectedTitle = "Kegiatan Kecamatan"
        selectedContent = "Informasi kegiatan dan program terbaru Kecamatan Medan Johor."
        detailKind = DetailKind.ACTIVITIES
        screen = AppScreen.DETAIL
        trackUsage("kegiatan")
    }

    fun openUmkm() {
        selectedTitle = "UMKM Binaan"
        selectedContent = "Direktori UMKM binaan Kecamatan Medan Johor."
        detailKind = DetailKind.UMKM
        screen = AppScreen.DETAIL
        trackUsage("umkm")
    }

    fun openRequirement(item: RequirementItem) {
        selectedRequirement = item
        selectedTitle = item.title.ifBlank { "Persyaratan ${item.code}" }
        selectedContent = item.content
        detailKind = DetailKind.STATIC
        trackUsage("persyaratan", "open")
    }

    fun openTourist(item: TouristItem) {
        selectedTourist = item
        selectedTitle = item.title.ifBlank { "Wisata ${item.code}" }
        selectedContent = item.content
        detailKind = DetailKind.STATIC
        trackUsage("wisata", "open")
    }

    fun openReport() {
        screen = AppScreen.REPORT
        message = null
        trackUsage("pengaduan")
    }

    fun openStatus() {
        screen = AppScreen.STATUS
        message = null
        loadReports()
        trackUsage("status")
    }

    fun loadReports() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.getReports(clientId) }
                .onSuccess { reports = it }
                .onFailure { message = UiMessage(it.message ?: "Gagal memuat status laporan.", true) }
        }
    }

    fun submitReport(
        name: String,
        contact: String,
        categoryId: String,
        wardId: String,
        description: String,
        latitude: Double,
        longitude: Double,
        photoBase64: String?,
        photoMime: String?,
    ) {
        busy = true
        message = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                api.submitReport(clientId, name, contact, categoryId, wardId, description, latitude, longitude, photoBase64, photoMime)
            }.onSuccess { report ->
                reports = listOf(report) + reports
                busy = false
                message = UiMessage("Laporan #${report.id.padStart(4, '0')} berhasil dikirim.")
                screen = AppScreen.STATUS
            }.onFailure { error ->
                busy = false
                message = UiMessage(error.message ?: "Laporan gagal dikirim.", true)
            }
        }
    }

    fun openIva() {
        ivaIndex = 0
        ivaAnswers = emptyMap()
        ivaResult = null
        message = null
        screen = AppScreen.IVA
        trackUsage("iva")
    }

    fun answerIva(value: String, name: String) {
        val questions = bootstrap?.ivaQuestions.orEmpty()
        val question = questions.getOrNull(ivaIndex) ?: return
        val nextAnswers = ivaAnswers + (question.id to value)
        ivaAnswers = nextAnswers
        if (ivaIndex < questions.lastIndex) {
            ivaIndex += 1
            return
        }

        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.submitIva(clientId, name.ifBlank { "Pengguna Aplikasi" }, nextAnswers) }
                .onSuccess {
                    ivaResult = it
                    busy = false
                }
                .onFailure {
                    busy = false
                    message = UiMessage(it.message ?: "Hasil IVA belum dapat disimpan.", true)
                }
        }
    }

    fun openChat() {
        screen = AppScreen.CHAT
        message = null
        trackUsage("livechat")
        refreshChat()
    }

    fun startChat(name: String) {
        if (name.trim().length < 2) {
            message = UiMessage("Masukkan nama terlebih dahulu.", true)
            return
        }
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.startChat(clientId, name.trim()) }
                .onSuccess {
                    chat = it
                    busy = false
                    beginChatPolling()
                }
                .onFailure {
                    busy = false
                    message = UiMessage(it.message ?: "LiveChat belum dapat dimulai.", true)
                }
        }
    }

    fun refreshChat() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.getChat(clientId) }
                .onSuccess { if (it != null) { chat = it; beginChatPolling() } }
                .onFailure { /* Tampilan tetap dapat dipakai untuk memulai sesi baru. */ }
        }
    }

    fun sendChat(text: String) {
        if (text.isBlank() || chat?.status != "active") return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.sendChat(clientId, text.trim()) }
                .onSuccess {
                    chat = it
                    busy = false
                    trackUsage("livechat", "message")
                }
                .onFailure {
                    busy = false
                    message = UiMessage(it.message ?: "Pesan belum terkirim.", true)
                }
        }
    }

    fun resetChat() {
        chatPolling?.cancel()
        chat = null
        message = null
    }

    fun closeChat() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.closeChat(clientId) }
                .onSuccess {
                    chat = chat?.copy(status = "closed")
                    chatPolling?.cancel()
                }
                .onFailure { message = UiMessage(it.message ?: "Sesi belum dapat ditutup.", true) }
        }
    }

    private fun beginChatPolling() {
        chatPolling?.cancel()
        chatPolling = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && chat?.status == "active") {
                delay(2_500)
                runCatching { api.getChat(clientId) }.onSuccess { latest ->
                    if (latest != null) chat = latest
                }
            }
        }
    }

    private fun trackUsage(feature: String, action: String = "view") {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.trackUsage(clientId, feature, action) }
        }
    }

    override fun onCleared() {
        chatPolling?.cancel()
        super.onCleared()
    }
}
