package com.ustagozu.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

val statusNames = listOf("Çözüldü", "Kısmen", "Çözülmedi")
fun fmt(t: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale("tr")).format(Date(t))

class MainActivity : ComponentActivity() {
    private var pending by mutableStateOf<ServiceRecord?>(null)
    private var themeMode by mutableIntStateOf(0) // 0 sistem, 1 gündüz, 2 gece

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        themeMode = prefs.getInt("theme", 0)
        handle(intent)
        setContent {
            val dark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                App(
                    themeMode,
                    { m -> themeMode = m; prefs.edit().putInt("theme", m).apply() },
                    pending,
                    { pending = null },
                    { openFile(it) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { openFile(it) }
    }

    private fun openFile(u: Uri) {
        Thread {
            val r = Share.read(this, u)
            runOnUiThread {
                if (r == null) {
                    Toast.makeText(this, "Geçersiz .ustagozu dosyası", Toast.LENGTH_LONG).show()
                } else {
                    pending?.let { Share.discard(this, it) }
                    pending = r
                }
            }
        }.start()
    }
}

@Composable
fun Field(label: String, v: String, kb: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(v, onChange, Modifier.fillMaxWidth().padding(vertical = 3.dp),
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = kb))
}

@Composable
fun Check(label: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.clickable { on(!v) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(v, on)
        Text(label)
    }
}

@Composable
fun Thumb(ctx: Context, name: String, onClick: () -> Unit) {
    val bmp by produceState<android.graphics.Bitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { Media.thumb(ctx, name) }
    }
    Box(
        Modifier.padding(end = 4.dp).size(80.dp).background(Color(0x22888888)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (Media.isVideo(name)) Text("▶", color = Color.White)
    }
}

@Composable
fun ThumbRow(ctx: Context, names: List<String>, onClick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        names.forEach { n -> Thumb(ctx, n) { onClick(n) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDialog(initial: ServiceRecord?, onSave: (ServiceRecord) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var c by remember { mutableStateOf(initial?.customer ?: "") }
    var a by remember { mutableStateOf(initial?.address ?: "") }
    var d by remember { mutableStateOf(initial?.device ?: "") }
    var f by remember { mutableStateOf(initial?.fault ?: "") }
    var s by remember { mutableStateOf(initial?.solution ?: "") }
    var p by remember { mutableStateOf(initial?.price ?: "") }
    var st by remember { mutableIntStateOf(initial?.status ?: 0) }
    var date by remember { mutableLongStateOf(initial?.date ?: System.currentTimeMillis()) }
    var picking by remember { mutableStateOf(false) }
    val oldMedia = initial?.mediaList() ?: emptyList()
    val media = remember { mutableStateListOf<String>().also { it.addAll(oldMedia) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        scope.launch {
            uris.forEach { u ->
                val n = withContext(Dispatchers.IO) { Media.copyIn(ctx, u) }
                if (n != null) media.add(n)
            }
        }
    }
    val cancel = {
        media.filter { it !in oldMedia }.forEach { Media.delete(ctx, it) }
        onCancel()
    }

    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(if (initial == null) "Yeni servis kaydı" else "Kaydı düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field("Müşteri", c) { c = it }
                Field("Adres", a) { a = it }
                Field("Cihaz (marka/model)", d) { d = it }
                Field("Arıza", f) { f = it }
                Field("Yapılan işlem / çözüm", s) { s = it }
                Field("Fiyat (TL)", p, KeyboardType.Decimal) { p = it }
                OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) { Text("Tarih: " + fmt(date)) }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    statusNames.forEachIndexed { i, n -> FilterChip(st == i, { st = i }, { Text(n) }, Modifier.padding(end = 4.dp)) }
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    Modifier.fillMaxWidth()
                ) { Text("Fotoğraf / Video ekle") }
                if (media.isNotEmpty()) {
                    ThumbRow(ctx, media.toList()) { n -> media.remove(n) }
                    Text("Kaldırmak için küçük resme dokun", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                oldMedia.filter { it !in media }.forEach { Media.delete(ctx, it) }
                onSave(
                    ServiceRecord(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        customer = c, address = a, device = d, fault = f, solution = s, price = p,
                        status = st, date = date, shared = initial?.shared ?: false,
                        media = media.joinToString("|")
                    )
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = cancel) { Text("İptal") } }
    )

    if (picking) {
        val ds = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { ds.selectedDateMillis?.let { date = it }; picking = false }) { Text("Tamam") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("İptal") } }
        ) { DatePicker(state = ds) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(themeMode: Int, onTheme: (Int) -> Unit, pending: ServiceRecord?, onPendingDone: () -> Unit, onPick: (Uri) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { Db.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val list by remember(q) { dao.search(q) }.collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServiceRecord?>(null) }
    var sel by remember { mutableStateOf<ServiceRecord?>(null) }
    var menu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) onPick(u) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("UstaGözü AI")
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮ Menü") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Dosya aç (.ustagozu)") }, onClick = { menu = false; picker.launch(arrayOf("*/*")) })
                            listOf("Tema: Sistem", "Tema: Gündüz", "Tema: Gece").forEachIndexed { i, n ->
                                DropdownMenuItem(text = { Text(n + if (themeMode == i) "  ✓" else "") }, onClick = { menu = false; onTheme(i) })
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Text("+") } }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Ara: model, arıza, müşteri, adres...") })
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) Text(if (q.isBlank()) "Henüz kayıt yok. + ile ekle." else "Bu aramayla eşleşen kayıt bulunamadı.")
            LazyColumn {
                items(list, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { sel = r }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(r.device, style = MaterialTheme.typography.titleMedium)
                            Text(r.fault)
                            Text(
                                statusNames[r.status] + " • " + fmt(r.date) +
                                    (if (r.price.isNotBlank()) " • " + r.price + " TL" else "") +
                                    (if (r.media.isNotEmpty()) " • 📎" + r.mediaList().size else "") +
                                    (if (r.shared) " • paylaşılan" else ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        FormDialog(
            initial = editing,
            onSave = { rec -> scope.launch { dao.upsert(rec) }; adding = false; editing = null },
            onCancel = { adding = false; editing = null }
        )
    }

    sel?.let { r ->
        var withC by remember(r.id) { mutableStateOf(false) }
        var withA by remember(r.id) { mutableStateOf(false) }
        var withP by remember(r.id) { mutableStateOf(false) }
        var withM by remember(r.id) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { sel = null },
            title = { Text(r.device) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Tarih: " + fmt(r.date))
                    if (r.customer.isNotBlank()) Text("Müşteri: " + r.customer)
                    if (r.address.isNotBlank()) Text("Adres: " + r.address)
                    Text("Arıza: " + r.fault)
                    Text("Çözüm: " + r.solution)
                    if (r.price.isNotBlank()) Text("Fiyat: " + r.price + " TL")
                    Text("Sonuç: " + statusNames[r.status])
                    if (r.media.isNotEmpty()) ThumbRow(ctx, r.mediaList()) { Media.open(ctx, it) }
                    Row {
                        TextButton(onClick = { editing = r; sel = null }) { Text("Düzenle") }
                        TextButton(onClick = {
                            scope.launch { dao.delete(r); withContext(Dispatchers.IO) { Media.deleteAll(ctx, r) } }
                            sel = null
                        }) { Text("Sil") }
                    }
                    Text("Gönderirken dahil et:", style = MaterialTheme.typography.labelLarge)
                    Check("Müşteri adı", withC) { withC = it }
                    Check("Adres", withA) { withA = it }
                    Check("Fiyat", withP) { withP = it }
                    Check("Fotoğraf / video", withM) { withM = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val i = withContext(Dispatchers.IO) { Share.build(ctx, r, withC, withA, withP, withM) }
                        ctx.startActivity(i)
                    }
                }) { Text("Gönder") }
            },
            dismissButton = { TextButton(onClick = { sel = null }) { Text("Kapat") } }
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { Share.discard(ctx, p); onPendingDone() },
            title = { Text("Gelen kayıt") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Cihaz: " + p.device)
                    if (p.address.isNotBlank()) Text("Adres: " + p.address)
                    Text("Arıza: " + p.fault)
                    Text("Çözüm: " + p.solution)
                    if (p.price.isNotBlank()) Text("Fiyat: " + p.price + " TL")
                    Text("Sonuç: " + statusNames[p.status])
                    if (p.media.isNotEmpty()) ThumbRow(ctx, p.mediaList()) { Media.open(ctx, it) }
                    Text("Arşivine eklensin mi?")
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { dao.upsert(p) }; onPendingDone() }) { Text("Ekle") } },
            dismissButton = { TextButton(onClick = { Share.discard(ctx, p); onPendingDone() }) { Text("Vazgeç") } }
        )
    }
}
