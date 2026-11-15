package com.odeme.takip

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private val GREEN = Color(0xFF16A34A)
private val RED = Color(0xFFDC2626)
private val AMBER = Color(0xFFD97706)

private val LightC = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFFE0E7FF), onSecondaryContainer = Color(0xFF1E1B4B),
    background = Color(0xFFF4F5FB), surface = Color.White, onSurface = Color(0xFF1B1E28),
    surfaceVariant = Color(0xFFEDEEF6), onSurfaceVariant = Color(0xFF6B7080),
    outline = Color(0xFFD7D9E5)
)
private val DarkC = darkColorScheme(
    primary = Color(0xFF8B93FF), onPrimary = Color(0xFF14163A),
    primaryContainer = Color(0xFF2B2F6B), onPrimaryContainer = Color(0xFFE0E7FF),
    secondaryContainer = Color(0xFF2B2F6B), onSecondaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF0F1117), surface = Color(0xFF1A1D27), onSurface = Color(0xFFEDEEF5),
    surfaceVariant = Color(0xFF252938), onSurfaceVariant = Color(0xFFA3A8BC),
    outline = Color(0xFF343848)
)

class MainActivity : ComponentActivity() {
    private val tick = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Reminder.schedule(this)
        val act = this
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkC else LightC) {
                var inside by remember { mutableStateOf(Auth.isLoggedIn(act)) }
                if (inside) {
                    App(tick.value) { Auth.setLoggedIn(act, false); inside = false }
                } else {
                    AuthScreen { inside = true }
                }
            }
        }
    }

    /** Bildirimden "Ödendi" denmiş olabilir; uygulama öne gelince verileri yeniden yükle. */
    override fun onResume() {
        super.onResume()
        tick.value = tick.value + 1
    }
}

// ───────────── Ana uygulama ─────────────

@Composable
fun App(refresh: Int, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    var txs by remember(refresh) { mutableStateOf(Store.loadTx(ctx)) }
    var pays by remember(refresh) { mutableStateOf(Store.loadPay(ctx)) }
    var tab by remember { mutableStateOf(0) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var txDlg by remember { mutableStateOf(false) }
    var txEdit by remember { mutableStateOf<Tx?>(null) }
    var payDlg by remember { mutableStateOf(false) }
    var payEdit by remember { mutableStateOf<Pay?>(null) }
    var upd by remember { mutableStateOf<UpdateInfo?>(null) }
    var prog by remember { mutableStateOf(-1) }
    var err by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Thread { upd = Updater.check(ctx) }.start() }

    fun setTx(l: List<Tx>) { txs = l; Store.saveTx(ctx, l) }
    fun setPay(l: List<Pay>) { pays = l; Store.savePay(ctx, l) }
    fun reload() { txs = Store.loadTx(ctx); pays = Store.loadPay(ctx) }
    fun toggle(p: Pay) {
        val ym = YearMonth.now().toString()
        if (ym in p.paid) {
            setPay(pays.map { if (it.id == p.id) it.copy(paid = it.paid - ym) else it })
            setTx(txs.filter { !(it.payId == p.id && it.date.startsWith(ym)) })
        } else {
            Store.markPaid(ctx, p.id)
            reload()
        }
    }
    fun checkUpdate() {
        msg = "Kontrol ediliyor..."
        Thread {
            val r = Updater.check(ctx)
            if (r != null) { upd = r; msg = "" } else msg = "Yeni sürüm bulunamadı."
        }.start()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("🏠" to "Özet", "🧾" to "İşlemler", "⏰" to "Ödemeler", "⚙️" to "Ayarlar")
                    .forEachIndexed { i, (e, t) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Text(e, fontSize = 20.sp) }, label = { Text(t) }
                        )
                    }
            }
        },
        floatingActionButton = {
            if (tab < 3) {
                FloatingActionButton(
                    onClick = {
                        if (tab == 2) { payEdit = null; payDlg = true } else { txEdit = null; txDlg = true }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Text("+", fontSize = 28.sp) }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> OzetTab(txs, pays, month) { month = it }
                1 -> IslemlerTab(txs, month, { month = it }) { txEdit = it; txDlg = true }
                2 -> OdemelerTab(pays, { payEdit = it; payDlg = true }) { toggle(it) }
                else -> SettingsTab(onLogout, { reload() }, { checkUpdate() }, msg)
            }
        }
    }

    val te = txEdit
    if (txDlg) {
        TxDialog(
            te, { txDlg = false },
            if (te != null) ({ setTx(txs.filter { it.id != te.id }); txDlg = false }) else null
        ) { t ->
            setTx(if (te == null) txs + t else txs.map { if (it.id == t.id) t else it })
            month = YearMonth.from(LocalDate.parse(t.date))
            txDlg = false
        }
    }
    val pe = payEdit
    if (payDlg) {
        PayDialog(
            pe, { payDlg = false },
            if (pe != null) ({ setPay(pays.filter { it.id != pe.id }); payDlg = false }) else null
        ) { p ->
            setPay(if (pe == null) pays + p else pays.map { if (it.id == p.id) p else it })
            payDlg = false
        }
    }

    upd?.let { u ->
        AlertDialog(
            onDismissRequest = { if (prog < 0) upd = null },
            title = { Text("Güncelleme var 🎉") },
            text = {
                Column {
                    Text("Yeni sürüm: " + u.name)
                    if (u.notes.isNotBlank()) Text(u.notes, Modifier.padding(top = 8.dp))
                    if (prog >= 0) {
                        LinearProgressIndicator(
                            progress = { prog / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                        Text("İndiriliyor %$prog")
                    }
                    if (err) Text("İndirme başarısız oldu, bağlantını kontrol edip tekrar dene.")
                }
            },
            confirmButton = {
                TextButton(enabled = prog < 0, onClick = {
                    err = false
                    if (!Updater.canInstall(ctx)) {
                        Updater.askPermission(ctx)
                    } else {
                        prog = 0
                        Thread {
                            val f = Updater.download(ctx, u.url) { p -> prog = p }
                            prog = -1
                            if (f != null) Updater.install(ctx, f) else err = true
                        }.start()
                    }
                }) { Text("İndir ve kur") }
            },
            dismissButton = { TextButton(enabled = prog < 0, onClick = { upd = null }) { Text("Sonra") } }
        )
    }
}

// ───────────── Ortak parçalar ─────────────

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val m = if (onClick != null) modifier.fillMaxWidth().clip(shape).clickable { onClick() } else modifier.fillMaxWidth()
    Card(
        modifier = m, shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun Pill(t: String, c: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(t, color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun IconBubble(e: String) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) { Text(e, fontSize = 20.sp) }
}

@Composable
fun Bar(p: Float, c: Color) {
    LinearProgressIndicator(
        progress = { p },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = c, trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun EmptyState(e: String, t: String, s: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(e, fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(s, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun statusColor(d: Int, lead: Int): Color =
    if (d < 0) RED else if (d <= lead) AMBER else MaterialTheme.colorScheme.primary

@Composable
fun MonthBar(m: YearMonth, set: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { set(m.minusMonths(1)) }) { Text("‹", fontSize = 22.sp) }
        Text(
            m.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("tr")) + " " + m.year,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
        )
        TextButton(onClick = { set(m.plusMonths(1)) }) { Text("›", fontSize = 22.sp) }
    }
}

private val pad16 = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp)

// ───────────── Özet ─────────────

@Composable
fun OzetTab(txs: List<Tx>, pays: List<Pay>, month: YearMonth, setMonth: (YearMonth) -> Unit) {
    val ctx = LocalContext.current
    val ms = month.toString()
    val nowS = YearMonth.now().toString()
    val mtx = txs.filter { it.date.startsWith(ms) }
    val inc = mtx.filter { it.income }.sumOf { it.amount }
    val exp = mtx.filter { !it.income }.sumOf { it.amount }
    val limit = Prefs.limit(ctx)
    val lead = Prefs.lead(ctx)
    val unpaid = pays.filter { nowS !in it.paid }.map { it to daysLeft(it) }.sortedBy { it.second }
    val cats = mtx.filter { !it.income }.groupBy { it.cat }
        .mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad16, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Merhaba, " + Auth.name(ctx) + " 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item { MonthBar(month, setMonth) }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))))
                    .padding(20.dp)
            ) {
                Column {
                    Text("Net bakiye", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(fmt(inc - exp), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("▲ Gelir", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                            Text(fmt(inc), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("▼ Gider", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                            Text(fmt(exp), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (limit > 0) {
            item {
                val ratio = (exp / limit).toFloat()
                val col = if (ratio >= 1f) RED else if (ratio >= 0.8f) AMBER else MaterialTheme.colorScheme.primary
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Aylık limit", fontWeight = FontWeight.Bold)
                        Text(fmt(exp) + " / " + fmt(limit), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Bar(ratio.coerceIn(0f, 1f), col)
                    Text(
                        if (exp > limit) fmt(exp - limit) + " aştın" else fmt(limit - exp) + " kaldı",
                        color = col, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (unpaid.isNotEmpty()) {
            item {
                AppCard {
                    Text("Bu ay ödenecekler", fontWeight = FontWeight.Bold)
                    Text(
                        unpaid.size.toString() + " ödeme · " + fmt(unpaid.sumOf { it.first.amount }),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    unpaid.take(4).forEach { (p, d) ->
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Medium)
                                Text(fmt(p.amount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill(daysText(d), statusColor(d, lead))
                        }
                    }
                }
            }
        }
        if (cats.isNotEmpty()) {
            item {
                AppCard {
                    Text("Gider dağılımı", fontWeight = FontWeight.Bold)
                    cats.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(emoji(e.key), fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(e.key, Modifier.weight(1f))
                            Text(fmt(e.value), fontWeight = FontWeight.Medium)
                        }
                        Bar((e.value / exp).toFloat(), MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            item { EmptyState("🪴", "Henüz işlem yok", "Sağ alttaki + ile ilk gelirini ya da giderini ekle.") }
        }
    }
}

// ───────────── İşlemler ─────────────

@Composable
fun IslemlerTab(txs: List<Tx>, month: YearMonth, setMonth: (YearMonth) -> Unit, onEdit: (Tx) -> Unit) {
    var f by remember { mutableStateOf(0) }
    val ms = month.toString()
    val l = txs.filter { it.date.startsWith(ms) && (f == 0 || (f == 1) == it.income) }.sortedByDescending { it.date }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad16, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MonthBar(month, setMonth) }
        item {
            Row {
                listOf("Tümü", "Gelir", "Gider").forEachIndexed { i, t ->
                    FilterChip(selected = f == i, onClick = { f = i }, label = { Text(t) })
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        if (l.isEmpty()) item { EmptyState("🧾", "Kayıt yok", "Bu ay için işlem bulunmuyor.") }
        items(l, key = { it.id }) { t ->
            AppCard(onClick = { onEdit(t) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(emoji(t.cat))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.cat, fontWeight = FontWeight.SemiBold)
                        Text(
                            trDate(t.date) + if (t.note.isNotBlank()) " · " + t.note else "",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        (if (t.income) "+" else "−") + fmt(t.amount),
                        color = if (t.income) GREEN else RED, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ───────────── Ödemeler ─────────────

@Composable
fun OdemelerTab(pays: List<Pay>, onEdit: (Pay) -> Unit, onToggle: (Pay) -> Unit) {
    val ctx = LocalContext.current
    val nowS = YearMonth.now().toString()
    val lead = Prefs.lead(ctx)
    val paidCount = pays.count { nowS in it.paid }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad16, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Aylık ödemeler", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (pays.isEmpty()) {
            item { EmptyState("⏰", "Ödeme yok", "Kira, fatura, kredi kartı gibi düzenli ödemelerini + ile ekle. Vadesi yaklaşınca bildirim gelir.") }
        } else {
            item {
                AppCard {
                    Text("Bu ay $paidCount / ${pays.size} ödeme tamamlandı", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Bar(paidCount.toFloat() / pays.size, GREEN)
                }
            }
        }
        items(pays.sortedBy { it.day }, key = { it.id }) { p ->
            val paid = nowS in p.paid
            val d = daysLeft(p)
            AppCard(onClick = { onEdit(p) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(if (paid) "✅" else "⏰")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text("Her ayın ${p.day}. günü", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(fmt(p.amount), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (paid) Pill("Bu ay ödendi", GREEN) else Pill(daysText(d), statusColor(d, lead))
                    FilledTonalButton(onClick = { onToggle(p) }) { Text(if (paid) "Geri al" else "Ödendi işaretle") }
                }
            }
        }
    }
}

// ───────────── Ayarlar ─────────────

@Composable
fun SettingsTab(onLogout: () -> Unit, onReload: () -> Unit, checkUpdate: () -> Unit, msg: String) {
    val ctx = LocalContext.current
    var hour by remember { mutableStateOf(Prefs.hour(ctx)) }
    var lead by remember { mutableStateOf(Prefs.lead(ctx)) }
    var limitText by remember { mutableStateOf(Prefs.limit(ctx).let { if (it > 0) numText(it) else "" }) }
    var info by remember { mutableStateOf("") }
    var restore by remember { mutableStateOf(false) }
    var resetDlg by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad16, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Ayarlar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            AppCard {
                Text("Hesap", fontWeight = FontWeight.Bold)
                Text(Auth.name(ctx) + " · " + Auth.email(ctx), color = muted, modifier = Modifier.padding(vertical = 6.dp))
                Pill("Hesap sahibi", MaterialTheme.colorScheme.primary)
                TextButton(onClick = onLogout) { Text("Çıkış yap") }
            }
        }
        item {
            AppCard {
                Text("Hatırlatma", fontWeight = FontWeight.Bold)
                Text("Bildirim saati", color = muted, modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { hour = (hour + 23) % 24; Prefs.setHour(ctx, hour); Reminder.schedule(ctx) }) { Text("−") }
                    Text("%02d:00".format(hour), modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { hour = (hour + 1) % 24; Prefs.setHour(ctx, hour); Reminder.schedule(ctx) }) { Text("+") }
                }
                Text("Kaç gün önceden hatırlat", color = muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row {
                    listOf(1, 3, 7).forEach { n ->
                        FilterChip(selected = lead == n, onClick = { lead = n; Prefs.setLead(ctx, n) }, label = { Text("$n gün") })
                        Spacer(Modifier.width(8.dp))
                    }
                }
                TextButton(onClick = { Reminder.check(ctx); info = "Hatırlatmalar kontrol edildi." }) { Text("Şimdi kontrol et (test)") }
            }
        }
        item {
            AppCard {
                Text("Aylık harcama limiti", fontWeight = FontWeight.Bold)
                Text("Boş bırakırsan limit kapalı olur.", color = muted)
                OutlinedTextField(
                    limitText, { limitText = it }, label = { Text("Limit (₺)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                TextButton(onClick = {
                    Prefs.setLimit(ctx, limitText.replace(',', '.').toDoubleOrNull() ?: 0.0)
                    info = "Limit kaydedildi."
                }) { Text("Kaydet") }
            }
        }
        item {
            AppCard {
                Text("Yedek", fontWeight = FontWeight.Bold)
                Text("Telefonunu değiştirirsen veya uygulamayı silersen verilerini buradan kurtarabilirsin.", color = muted, modifier = Modifier.padding(vertical = 6.dp))
                Row {
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Ödeme Takip yedek")
                            .putExtra(Intent.EXTRA_TEXT, Store.exportAll(ctx))
                        ctx.startActivity(Intent.createChooser(send, "Yedeği paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Yedeği paylaş") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { restore = true }) { Text("Geri yükle") }
                }
            }
        }
        item {
            AppCard {
                Text("Güncelleme", fontWeight = FontWeight.Bold)
                Text("Sürüm " + Updater.currentName(ctx), color = muted, modifier = Modifier.padding(vertical = 4.dp))
                TextButton(onClick = checkUpdate) { Text("Güncellemeyi kontrol et") }
                if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelMedium, color = muted)
            }
        }
        item {
            AppCard {
                Text("Neden Ödeme Takip?", fontWeight = FontWeight.Bold)
                Text(
                    "🔒 Verilerin sadece bu telefonda durur.\n🚫 Reklam yok, abonelik yok.\n" +
                        "⏰ Uygulama kapalıyken bile hatırlatır; bildirimden tek dokunuşla \"Ödendi\" denir.\n" +
                        "🇹🇷 Türkçe ve ₺ odaklı.",
                    color = muted, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (info.isNotEmpty()) item { Text(info, color = GREEN, modifier = Modifier.padding(horizontal = 4.dp)) }
        item {
            TextButton(onClick = { resetDlg = true }) { Text("Hesabı ve tüm verileri sil", color = RED) }
        }
    }

    if (restore) {
        var txt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { restore = false },
            title = { Text("Yedekten geri yükle") },
            text = {
                OutlinedTextField(txt, { txt = it }, label = { Text("Yedek metnini yapıştır") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Store.importAll(ctx, txt)) { onReload(); info = "Yedek geri yüklendi."; restore = false }
                    else info = "Geçersiz yedek metni."
                }) { Text("Geri yükle") }
            },
            dismissButton = { TextButton(onClick = { restore = false }) { Text("İptal") } }
        )
    }
    if (resetDlg) {
        AlertDialog(
            onDismissRequest = { resetDlg = false },
            title = { Text("Her şey silinsin mi?") },
            text = { Text("Hesabın ve bu telefondaki tüm gelir, gider ve ödeme kayıtların silinir. Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { Auth.reset(ctx); resetDlg = false; onLogout() }) { Text("Sil", color = RED) } },
            dismissButton = { TextButton(onClick = { resetDlg = false }) { Text("Vazgeç") } }
        )
    }
}

// ───────────── Diyaloglar ─────────────

@Composable
fun TxDialog(init: Tx?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Tx) -> Unit) {
    var income by remember { mutableStateOf(init?.income ?: false) }
    var amt by remember { mutableStateOf(if (init != null) numText(init.amount) else "") }
    var cat by remember { mutableStateOf(init?.cat ?: CG[0]) }
    var date by remember { mutableStateOf(init?.date ?: LocalDate.now().toString()) }
    var note by remember { mutableStateOf(init?.note ?: "") }
    val cats = if (income) CI else CG
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (init == null) "Yeni işlem" else "İşlemi düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    FilterChip(selected = !income, onClick = { income = false; cat = CG[0] }, label = { Text("Gider") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = income, onClick = { income = true; cat = CI[0] }, label = { Text("Gelir") })
                }
                OutlinedTextField(
                    amt, { amt = it }, label = { Text("Tutar (₺)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    cats.forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(emoji(c) + " " + c) })
                        Spacer(Modifier.width(6.dp))
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Tarih (YYYY-AA-GG)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Not") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Bu işlemi sil", color = RED) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amt.replace(',', '.').toDoubleOrNull()
                val d = try { LocalDate.parse(date) } catch (e: Exception) { null }
                if (a != null && a > 0 && d != null)
                    onSave(Tx(init?.id ?: newId(), income, a, cat, d.toString(), note.trim(), init?.payId ?: ""))
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun PayDialog(init: Pay?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Pay) -> Unit) {
    var name by remember { mutableStateOf(init?.name ?: "") }
    var amt by remember { mutableStateOf(if (init != null) numText(init.amount) else "") }
    var day by remember { mutableStateOf(init?.day?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (init == null) "Yeni aylık ödeme" else "Ödemeyi düzenle") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Ad (kira, internet...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amt, { amt = it }, label = { Text("Tutar (₺)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    day, { day = it }, label = { Text("Ayın kaçıncı günü (1-31)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Bu ödemeyi sil", color = RED) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amt.replace(',', '.').toDoubleOrNull()
                val d = day.toIntOrNull()
                if (name.isNotBlank() && a != null && a > 0 && d != null && d in 1..31)
                    onSave(Pay(init?.id ?: newId(), name.trim(), a, d, init?.paid ?: emptySet()))
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

// ───────────── Giriş / Kayıt ─────────────

@Composable
fun AuthScreen(onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var acc by remember { mutableStateOf(Auth.hasAccount(ctx)) }
    var tab by remember { mutableStateOf(if (acc) 0 else 1) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("💰 Ödeme Takip", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; err = "" }, text = { Text("Giriş") })
                Tab(selected = tab == 1, onClick = { tab = 1; err = "" }, text = { Text("Kayıt ol") })
            }
            Spacer(Modifier.height(16.dp))
            if (tab == 0 && !acc) {
                Text("Bu telefonda hesap yok. Kayıt ol sekmesinden hesap oluştur.")
            } else if (tab == 1 && acc) {
                Text("Bu telefonda zaten bir hesap var. Yeni hesap açmak için Giriş sekmesinden hesabı sıfırlaman gerekir.")
            } else {
                if (tab == 1) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Ad") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    email, { email = it }, label = { Text("E-posta") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Şifre") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (tab == 1) {
                    OutlinedTextField(
                        pass2, { pass2 = it }, label = { Text("Şifre tekrar") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (err.isNotEmpty()) {
                    Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                Button(
                    onClick = {
                        if (tab == 0) {
                            if (Auth.login(ctx, email, pass)) onDone() else err = "E-posta veya şifre hatalı."
                        } else {
                            when {
                                name.isBlank() -> err = "Adını yaz."
                                !email.contains("@") || !email.contains(".") -> err = "Geçerli bir e-posta yaz."
                                pass.length < 6 -> err = "Şifre en az 6 karakter olmalı."
                                pass != pass2 -> err = "Şifreler aynı değil."
                                else -> { Auth.register(ctx, name.trim(), email, pass); onDone() }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(if (tab == 0) "Giriş yap" else "Kayıt ol") }
                if (tab == 0) {
                    TextButton(onClick = { confirm = true }) { Text("Şifremi unuttum / hesabı sıfırla") }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Hesap sıfırlansın mı?") },
            text = { Text("Hesap ve bu telefondaki tüm gelir, gider ve ödeme kayıtların silinir. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    Auth.reset(ctx)
                    acc = false; tab = 1; confirm = false; err = ""
                }) { Text("Sil ve sıfırla") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } }
        )
    }
}
