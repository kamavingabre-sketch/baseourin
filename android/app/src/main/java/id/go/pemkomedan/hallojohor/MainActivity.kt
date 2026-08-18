package id.go.pemkomedan.hallojohor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HalloJohorTheme { HalloJohorApp() } }
    }
}

private val Blue = Color(0xFF0EA5E9)
private val Green = Color(0xFF059669)
private val Navy = Color(0xFF0F172A)
private val SoftBlue = Color(0xFFE0F2FE)

@Composable
private fun HalloJohorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Blue,
            secondary = Green,
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            onSurface = Navy,
        ),
        content = content,
    )
}

@Composable
private fun HalloJohorApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val title = when (vm.screen) {
        AppScreen.HOME -> "Hallo Johor"
        AppScreen.DETAIL -> vm.selectedTitle
        AppScreen.REPORT -> "Pengaduan Masyarakat"
        AppScreen.IVA -> "Skrining IVA Test"
        AppScreen.CHAT -> "LiveChat Admin"
        AppScreen.STATUS -> "Status Laporan"
    }

    BackHandler(enabled = vm.screen != AppScreen.HOME) { vm.goHome() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (vm.screen != AppScreen.HOME) {
                        IconButton(onClick = vm::goHome) { Icon(Icons.Default.ArrowBack, "Kembali") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Navy,
                    navigationIconContentColor = Blue,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            vm.message?.let { banner ->
                MessageBanner(banner) { vm.dismissMessage() }
            }
            Box(Modifier.fillMaxSize()) {
                when (vm.screen) {
                    AppScreen.HOME -> HomeScreen(vm)
                    AppScreen.DETAIL -> DetailScreen(vm, context)
                    AppScreen.REPORT -> ReportScreen(vm)
                    AppScreen.IVA -> IvaScreen(vm)
                    AppScreen.CHAT -> ChatScreen(vm)
                    AppScreen.STATUS -> StatusScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: UiMessage, onDismiss: () -> Unit) {
    val background = if (message.isError) Color(0xFFFFE4E6) else Color(0xFFDCFCE7)
    val foreground = if (message.isError) Color(0xFFBE123C) else Color(0xFF166534)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = foreground)
            Spacer(Modifier.width(8.dp))
            Text(message.text, Modifier.weight(1f), color = foreground, fontSize = 13.sp)
            TextButton(onClick = onDismiss) { Text("Tutup", color = foreground) }
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel) {
    val data = vm.bootstrap
    if (vm.loading && data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue) }
        return
    }
    if (data == null) {
        EmptyState("Layanan belum dapat dimuat.", "Coba lagi") { vm.loadBootstrap() }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Navy),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("🏙️  HALLO JOHOR", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Layanan digital Kecamatan Medan Johor untuk warga.", color = Color(0xFFBAE6FD), fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("#MEDANUNTUKSEMUA", color = Color(0xFF86EFAC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { SectionTitle("Layanan utama") }
        item {
            ActionCard("📢", "Pengaduan Masyarakat", "Laporkan masalah di wilayah Anda") { vm.openReport() }
        }
        item {
            ActionCard("💬", "LiveChat Admin", "Chat langsung dengan petugas kecamatan") { vm.openChat() }
        }
        item {
            ActionCard("📋", "Status Laporan Saya", "Lihat riwayat dan status laporan") { vm.openStatus() }
        }
        item { SectionTitle("Informasi warga") }
        items(data.services, key = { it.id }) { service ->
            ActionCard(service.icon, service.title, "Buka informasi layanan") { vm.openService(service) }
        }
        item {
            ActionCard("🎪", "Kegiatan Kecamatan", "Jadwal dan program terbaru") { vm.openActivities() }
        }
        item {
            ActionCard("🏪", "UMKM Binaan", "Direktori usaha mikro binaan kecamatan") { vm.openUmkm() }
        }
        item { SectionTitle("Kesehatan") }
        item {
            ActionCard("🎗️", "Skrining IVA Test", "7 pertanyaan singkat — bukan diagnosis medis") { vm.openIva() }
        }
        item {
            Text(
                "Data penggunaan fitur dicatat secara anonim agar layanan Kecamatan Medan Johor dapat dievaluasi.",
                color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = Navy, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ActionCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp, modifier = Modifier.width(42.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color(0xFF64748B), fontSize = 12.sp)
            }
            Text("›", fontSize = 26.sp, color = Blue)
        }
    }
}

@Composable
private fun DetailScreen(vm: MainViewModel, context: Context) {
    val data = vm.bootstrap ?: return
    val requirement = vm.selectedRequirement
    val tourist = vm.selectedTourist
    if (requirement != null || tourist != null || vm.detailKind == DetailKind.STATIC) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ContentCard(vm.selectedTitle, vm.selectedContent)
            if (vm.selectedTitle.contains("Kontak", ignoreCase = true)) {
                Spacer(Modifier.height(12.dp))
                ContactActions(data.contact, context)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (vm.detailKind) {
            DetailKind.REQUIREMENTS -> {
                item { ContentCard(vm.selectedTitle, vm.selectedContent) }
                item { SectionTitle("Pilih jenis surat") }
                items(data.requirements, key = { it.code }) { item ->
                    CompactItem("${item.code}  ${item.title}") { vm.openRequirement(item) }
                }
            }
            DetailKind.TOURISM -> {
                item { ContentCard(vm.selectedTitle, vm.selectedContent) }
                item { SectionTitle("Pilih kategori wisata") }
                items(data.wisata, key = { it.code }) { item ->
                    CompactItem("${item.code}  ${item.title}") { vm.openTourist(item) }
                }
            }
            DetailKind.ACTIVITIES -> {
                item { Text(vm.selectedContent, color = Color(0xFF64748B), fontSize = 13.sp) }
                if (data.activities.isEmpty()) item { EmptyState("Belum ada kegiatan terbaru.") }
                items(data.activities, key = { it.id }) { activity -> ActivityCard(activity) }
            }
            DetailKind.UMKM -> {
                item { Text(vm.selectedContent, color = Color(0xFF64748B), fontSize = 13.sp) }
                if (data.umkm.isEmpty()) item { EmptyState("Belum ada data UMKM.") }
                items(data.umkm, key = { it.id }) { item -> UmkmCard(item, context) }
            }
            DetailKind.STATIC -> Unit
        }
    }
}

@Composable
private fun ContentCard(title: String, content: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Navy, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(cleanMarkup(content), color = Color(0xFF334155), fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun CompactItem(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SoftBlue),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MenuBook, null, tint = Blue)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Navy, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("›", color = Blue, fontSize = 24.sp)
        }
    }
}

@Composable
private fun ActivityCard(item: ActivityItem) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
        Column(Modifier.padding(16.dp)) {
            Text("📌  ${item.name}", color = Navy, fontWeight = FontWeight.Bold)
            if (item.date.isNotBlank()) Text("📅 ${item.date}", color = Blue, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            if (item.place.isNotBlank()) Text("📍 ${item.place}", color = Color(0xFF475569), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            if (item.description.isNotBlank()) Text(item.description, color = Color(0xFF475569), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun UmkmCard(item: UmkmItem, context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
        Column(Modifier.padding(16.dp)) {
            Text("🏪  ${item.name}", color = Navy, fontWeight = FontWeight.Bold)
            if (item.category.isNotBlank()) Text("🏷️ ${item.category}", color = Color(0xFFB45309), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            if (item.address.isNotBlank()) Text("📍 ${item.address}", color = Color(0xFF475569), fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            if (item.contact.isNotBlank()) Text("📱 ${item.contact}", color = Color(0xFF475569), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (item.mapsUrl.isNotBlank()) TextButton(onClick = { openUrl(context, item.mapsUrl) }) { Text("Buka Google Maps", color = Blue) }
        }
    }
}

@Composable
private fun ContactActions(contact: ContactInfo, context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = SoftBlue)) {
        Column(Modifier.padding(16.dp)) {
            Text(contact.office, fontWeight = FontWeight.Bold, color = Navy)
            Text(contact.address, color = Color(0xFF334155), fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            Text(contact.serviceHours, color = Color(0xFF334155), fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                OutlinedButton(onClick = { openUrl(context, "tel:${contact.phone}") }) {
                    Icon(Icons.Default.Call, null); Spacer(Modifier.width(5.dp)); Text("Telepon")
                }
                OutlinedButton(onClick = { openUrl(context, contact.mapsUrl) }) {
                    Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(5.dp)); Text("Peta")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportScreen(vm: MainViewModel) {
    val data = vm.bootstrap ?: return
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var wardId by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf<String?>(null) }
    var photoMime by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var wardExpanded by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Foto tidak dapat dibaca")
            if (bytes.size > 5 * 1024 * 1024) error("Ukuran foto maksimal 5 MB")
            photoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            photoMime = context.contentResolver.getType(uri) ?: "image/jpeg"
        }.onFailure { formError = it.message ?: "Foto tidak valid" }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            val location = readLastLocation(context)
            if (location != null) {
                latitude = formatCoordinate(location.latitude)
                longitude = formatCoordinate(location.longitude)
                formError = ""
            } else formError = "Lokasi belum tersedia. Aktifkan GPS atau isi koordinat manual."
        } else formError = "Izin lokasi diperlukan untuk laporan."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Laporkan masalah di wilayah Anda. Laporan akan tersimpan di Dashboard Admin.", color = Color(0xFF475569), fontSize = 13.sp)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama pelapor *") }, singleLine = true)
        OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), label = { Text("Nomor yang dapat dihubungi") }, singleLine = true)

        ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
            val selected = data.categories.find { it.id == categoryId }
            OutlinedTextField(
                value = selected?.let { "${it.emoji} ${it.label}" } ?: "",
                onValueChange = {}, readOnly = true, label = { Text("Kategori *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                data.categories.forEach { item ->
                    DropdownMenuItem(
                        text = { Text("${item.emoji} ${item.label}") },
                        onClick = { categoryId = item.id; categoryExpanded = false },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(expanded = wardExpanded, onExpandedChange = { wardExpanded = !wardExpanded }) {
            val selected = data.wards.find { it.id == wardId }
            OutlinedTextField(
                value = selected?.label ?: "", onValueChange = {}, readOnly = true, label = { Text("Kelurahan *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(wardExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = wardExpanded, onDismissRequest = { wardExpanded = false }) {
                data.wards.forEach { item ->
                    DropdownMenuItem(text = { Text(item.label) }, onClick = { wardId = item.id; wardExpanded = false })
                }
            }
        }

        OutlinedTextField(
            description, { if (it.length <= 2000) description = it }, Modifier.fillMaxWidth(),
            label = { Text("Uraian laporan *") }, minLines = 4,
        )

        Text("Lokasi kejadian *", fontWeight = FontWeight.Bold, color = Navy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f), label = { Text("Latitude") }, singleLine = true)
            OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f), label = { Text("Longitude") }, singleLine = true)
        }
        OutlinedButton(onClick = {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                val location = readLastLocation(context)
                if (location != null) {
                    latitude = formatCoordinate(location.latitude); longitude = formatCoordinate(location.longitude); formError = ""
                } else formError = "Lokasi belum tersedia. Aktifkan GPS atau isi manual."
            } else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("Ambil lokasi saya")
        }

        OutlinedButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text(if (photoBase64 == null) "Lampirkan foto (opsional)" else "Foto sudah dipilih ✓")
        }
        if (formError.isNotBlank()) Text(formError, color = Color(0xFFBE123C), fontSize = 12.sp)
        Button(
            onClick = {
                val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull()
                formError = when {
                    name.trim().length < 2 -> "Nama wajib diisi."
                    categoryId.isBlank() -> "Pilih kategori laporan."
                    wardId.isBlank() -> "Pilih kelurahan."
                    description.trim().length < 5 -> "Uraian minimal 5 karakter."
                    lat == null || lon == null -> "Isi lokasi dengan tombol Ambil lokasi atau koordinat manual."
                    else -> ""
                }
                if (formError.isBlank() && lat != null && lon != null) {
                    vm.submitReport(name.trim(), contact.trim(), categoryId, wardId, description.trim(), lat, lon, photoBase64, photoMime)
                }
            }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Kirim laporan")
        }
        Text("Dengan mengirim laporan, Anda menyetujui data laporan digunakan untuk tindak lanjut pelayanan publik.", color = Color(0xFF64748B), fontSize = 11.sp)
    }
}

@Composable
private fun IvaScreen(vm: MainViewModel) {
    val questions = vm.bootstrap?.ivaQuestions.orEmpty()
    var name by rememberSaveable { mutableStateOf("") }
    val result = vm.ivaResult
    if (result != null) {
        val riskLabel = when (result.risk) { "tinggi" -> "🔴 TINGGI"; "sedang" -> "🟡 SEDANG"; else -> "🟢 RENDAH" }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = if (result.risk == "tinggi") Color(0xFFFFE4E6) else SoftBlue)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Hasil Skrining Anda", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Navy)
                    Spacer(Modifier.height(12.dp)); Text(riskLabel, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
                    Text("Skor ${result.score}/12", color = Color(0xFF475569), modifier = Modifier.padding(top = 6.dp))
                }
            }
            Text("Skrining ini bukan diagnosis medis. Konsultasikan hasilnya kepada tenaga kesehatan dan kunjungi Puskesmas bila diperlukan.", color = Color(0xFF475569), fontSize = 13.sp, lineHeight = 20.sp)
            OutlinedButton(onClick = vm::openIva, modifier = Modifier.fillMaxWidth()) { Text("Ulangi skrining") }
        }
        return
    }
    val question = questions.getOrNull(vm.ivaIndex)
    if (question == null) {
        EmptyState("Pertanyaan IVA belum tersedia.")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("7 pertanyaan singkat untuk mengenali faktor risiko umum.", color = Color(0xFF475569), fontSize = 13.sp)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama (opsional)") }, singleLine = true)
        Text("Pertanyaan ${vm.ivaIndex + 1} dari ${questions.size}", color = Blue, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = SoftBlue)) {
            Text(cleanMarkup(question.text), Modifier.padding(16.dp), color = Navy, fontSize = 15.sp, lineHeight = 22.sp)
        }
        question.options.forEach { option ->
            OutlinedButton(onClick = { vm.answerIva(option.value, name) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text(option.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }
        Text("Anda dapat kembali kapan saja. Data hasil dicatat untuk evaluasi layanan kesehatan.", color = Color(0xFF64748B), fontSize = 11.sp)
    }
}

@Composable
private fun ChatScreen(vm: MainViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    val session = vm.chat
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hubungi petugas Kecamatan Medan Johor melalui LiveChat. Balasan admin akan muncul otomatis.", color = Color(0xFF475569), fontSize = 13.sp)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama Anda") }, singleLine = true)
            Button(onClick = { vm.startChat(name) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Mulai LiveChat") }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (session.status == "closed") {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))) {
                Column(Modifier.padding(14.dp)) {
                    Text("Sesi telah ditutup oleh admin.", color = Color(0xFF9A3412), fontWeight = FontWeight.Bold)
                    TextButton(onClick = vm::resetChat) { Text("Mulai sesi baru") }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(session.messages, key = { it.id }) { item ->
                val admin = item.from == "admin"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (admin) Arrangement.End else Arrangement.Start) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (admin) SoftBlue else Color.White), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                        Column(Modifier.padding(11.dp).widthIn(max = 290.dp)) {
                            Text(if (admin) "Admin" else session.name, color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(item.text, color = Navy, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
        if (session.status == "active") {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Ketik pesan...") }, singleLine = true)
                IconButton(onClick = { if (input.isNotBlank()) { vm.sendChat(input); input = "" } }, enabled = !vm.busy) { Icon(Icons.Default.Send, "Kirim", tint = Blue) }
            }
            TextButton(onClick = vm::closeChat, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Tutup sesi", color = Color(0xFFBE123C)) }
        }
    }
}

@Composable
private fun StatusScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Laporan yang dikirim dari aplikasi ini", Modifier.weight(1f), color = Color(0xFF475569), fontSize = 13.sp)
            IconButton(onClick = vm::loadReports) { Icon(Icons.Default.Refresh, "Refresh", tint = Blue) }
        }
        if (vm.reports.isEmpty()) {
            EmptyState("Belum ada laporan dari aplikasi ini.", null)
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.reports, key = { it.id }) { report -> ReportCard(report) }
            }
        }
    }
}

@Composable
private fun ReportCard(report: ReportItem) {
    val color = when (report.status) { "selesai" -> Color(0xFF166534); "ditolak" -> Color(0xFFBE123C); "diproses" -> Color(0xFFB45309); else -> Blue }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${report.id.padStart(4, '0')}", color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(statusLabel(report.status), color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text("${report.category} • ${report.ward}", color = Blue, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            Text(report.description, color = Color(0xFF334155), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            if (report.date.isNotBlank()) Text(report.date, color = Color(0xFF64748B), fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
        }
    }
}

@Composable
private fun EmptyState(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("📭", fontSize = 34.sp)
        Text(text, color = Color(0xFF64748B), textAlign = TextAlign.Center)
        if (action != null && onAction != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

private fun statusLabel(status: String) = when (status) {
    "terkirim" -> "📨 Terkirim"
    "diproses" -> "⚙️ Diproses"
    "selesai" -> "✅ Selesai"
    "ditolak" -> "❌ Ditolak"
    else -> status
}

private fun cleanMarkup(value: String): String = value
    .replace("*", "")
    .replace("_", "")
    .replace("━━━━━━━━━━━━━━━━━━━━━━━", "────────────────────────")

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun readLastLocation(context: Context): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null
    return manager.getProviders(true).mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
