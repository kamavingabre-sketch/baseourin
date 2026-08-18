package id.go.pemkomedan.hallojohor

/** Model yang dikirim oleh /api/mobile/bootstrap. */
data class ServiceItem(
    val id: String,
    val title: String,
    val icon: String,
    val content: String,
)

data class RequirementItem(val code: String, val title: String, val content: String)
data class TouristItem(val code: String, val title: String, val content: String)
data class CategoryItem(val id: String, val label: String, val emoji: String)
data class WardItem(val id: String, val label: String)
data class ActivityItem(
    val id: String,
    val name: String,
    val description: String,
    val place: String,
    val date: String,
)
data class UmkmItem(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val mapsUrl: String,
    val contact: String,
)
data class IvaOption(val value: String, val label: String)
data class IvaQuestion(val id: String, val text: String, val options: List<IvaOption>)
data class ContactInfo(
    val office: String,
    val address: String,
    val phone: String,
    val email: String,
    val mapsUrl: String,
    val serviceHours: String,
)
data class BootstrapData(
    val services: List<ServiceItem>,
    val requirements: List<RequirementItem>,
    val wisata: List<TouristItem>,
    val categories: List<CategoryItem>,
    val wards: List<WardItem>,
    val activities: List<ActivityItem>,
    val umkm: List<UmkmItem>,
    val ivaQuestions: List<IvaQuestion>,
    val contact: ContactInfo,
)

data class ReportItem(
    val id: String,
    val name: String,
    val contact: String,
    val category: String,
    val ward: String,
    val description: String,
    val address: String,
    val status: String,
    val date: String,
)

data class ChatMessage(
    val id: String,
    val from: String,
    val text: String,
    val timestamp: String,
)
data class ChatSession(
    val id: String,
    val name: String,
    val status: String,
    val messages: List<ChatMessage>,
)
data class IvaResult(val score: Int, val risk: String)

enum class AppScreen {
    HOME, DETAIL, REPORT, IVA, CHAT, STATUS
}

data class UiMessage(val text: String, val isError: Boolean = false)
