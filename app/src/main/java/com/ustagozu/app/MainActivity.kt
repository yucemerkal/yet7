package com.ustagozu.app

import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val statusNames = listOf("Çözüldü", "Kısmen", "Çözülmedi")
fun fmt(t: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale("tr")).format(Date(t))

class MainActivity : ComponentActivity() {
    private var pending by mutableStateOf<ServiceRecord?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            MaterialTheme {
                App(pending, { pending = null }, { openFile(it) })
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
        val r = Share.read(this, u)
        if (r == null) Toast.makeText(this, "Geçersiz .ustagozu dosyası", Toast.LENGTH_LONG).show()
        pending = r
    }
}

@Composable
fun Field(label: String, v: String, onChange: (String) -> Unit) {
    OutlinedTextField(v, onChange, Modifier.fillMaxWidth().padding(vertical = 3.dp), label = { Text(label) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(pending: ServiceRecord?, onPendingDone: () -> Unit, onPick: (Uri) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { Db.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val list by remember(q) { dao.search(q) }.collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<ServiceRecord?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) onPick(u) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UstaGözü AI") },
                actions = { TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Dosya aç") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Text("+") } }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Ara: model, arıza, müşteri...") })
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) Text(if (q.isBlank()) "Henüz kayıt yok. + ile ekle." else "Bu aramayla eşleşen kayıt bulunamadı.")
            LazyColumn {
                items(list, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { sel = r }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(r.device, style = MaterialTheme.typography.titleMedium)
                            Text(r.fault)
                            Text(statusNames[r.status] + " • " + fmt(r.date) + (if (r.shared) " • paylaşılan" else ""),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        var c by remember { mutableStateOf("") }
        var d by remember { mutableStateOf("") }
        var f by remember { mutableStateOf("") }
        var s by remember { mutableStateOf("") }
        var st by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Yeni servis kaydı") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Field("Müşteri", c) { c = it }
                    Field("Cihaz (marka/model)", d) { d = it }
                    Field("Arıza", f) { f = it }
                    Field("Yapılan işlem / çözüm", s) { s = it }
                    Row { statusNames.forEachIndexed { i, n -> FilterChip(st == i, { st = i }, { Text(n) }, Modifier.padding(end = 4.dp)) } }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.upsert(ServiceRecord(customer = c, device = d, fault = f, solution = s, status = st)) }
                    adding = false
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("İptal") } }
        )
    }

    sel?.let { r ->
        var withC by remember(r.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { sel = null },
            title = { Text(r.device) },
            text = {
                Column {
                    Text("Müşteri: " + r.customer)
                    Text("Arıza: " + r.fault)
                    Text("Çözüm: " + r.solution)
                    Text("Sonuç: " + statusNames[r.status])
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(withC, { withC = it })
                        Text("Gönderirken müşteri bilgisini dahil et")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ctx.startActivity(Share.build(ctx, r, withC)) }) { Text("Gönder") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { scope.launch { dao.delete(r) }; sel = null }) { Text("Sil") }
                    TextButton(onClick = { sel = null }) { Text("Kapat") }
                }
            }
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = onPendingDone,
            title = { Text("Gelen kayıt") },
            text = {
                Column {
                    Text("Cihaz: " + p.device)
                    Text("Arıza: " + p.fault)
                    Text("Çözüm: " + p.solution)
                    Text("Sonuç: " + statusNames[p.status])
                    Text("Arşivine eklensin mi?")
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { dao.upsert(p) }; onPendingDone() }) { Text("Ekle") } },
            dismissButton = { TextButton(onClick = onPendingDone) { Text("Vazgeç") } }
        )
    }
}
