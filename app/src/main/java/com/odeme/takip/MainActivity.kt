package com.odeme.takip

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Reminder.schedule(this)
        val act = this
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                var inside by remember { mutableStateOf(Auth.isLoggedIn(act)) }
                if (inside) {
                    App(onLogout = { Auth.setLoggedIn(act, false); inside = false })
                } else {
                    AuthScreen { inside = true }
                }
            }
        }
    }
}

private val GREEN = Color(0xFF16A34A)
private val RED = Color(0xFFDC2626)

@Composable
fun App(onLogout: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var txs by remember { mutableStateOf(Store.loadTx(ctx)) }
    var pays by remember { mutableStateOf(Store.loadPay(ctx)) }
    var tab by remember { mutableStateOf(0) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var dlg by remember { mutableStateOf(false) }
    var upd by remember { mutableStateOf<UpdateInfo?>(null) }
    var prog by remember { mutableStateOf(-1) }
    var err by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Thread { upd = Updater.check(ctx) }.start() }

    fun setTx(l: List<Tx>) { txs = l; Store.saveTx(ctx, l) }
    fun setPay(l: List<Pay>) { pays = l; Store.savePay(ctx, l) }

    fun toggle(p: Pay) {
        val ym = YearMonth.now().toString()
        if (ym in p.paid) {
            setPay(pays.map { if (it.id == p.id) it.copy(paid = it.paid - ym) else it })
            setTx(txs.filter { !(it.payId == p.id && it.date.startsWith(ym)) })
        } else {
            setPay(pays.map { if (it.id == p.id) it.copy(paid = it.paid + ym) else it })
            setTx(txs + Tx(newId(), false, p.amount, "Fatura", LocalDate.now().toString(), p.name, p.id))
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Özet", "İşlemler", "Ödemeler").forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(t) })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dlg = true }) { Text("+", fontSize = 26.sp) }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            val ms = month.toString()
            val nowS = YearMonth.now().toString()
            val mtx = txs.filter { it.date.startsWith(ms) }.sortedByDescending { it.date }
            when (tab) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Merhaba, " + Auth.name(ctx),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    MonthBar(month) { month = it }
                    val inc = mtx.filter { it.income }.sumOf { it.amount }
                    val exp = mtx.filter { !it.income }.sumOf { it.amount }
                    Stat("Gelir", inc, GREEN)
                    Stat("Gider", exp, RED)
                    Stat("Net", inc - exp, if (inc - exp < 0) RED else GREEN)
                    val due = pays.filter { nowS !in it.paid }
                        .map { it to daysLeft(it) }.filter { it.second <= 3 }.sortedBy { it.second }
                    if (due.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("⏰ Yaklaşan ödemeler", fontWeight = FontWeight.Bold)
                                due.forEach { (p, d) ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(p.name + " · " + daysText(d))
                                        Text(fmt(p.amount), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Sürüm " + Updater.currentName(ctx),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    TextButton(onClick = {
                        msg = "Kontrol ediliyor..."
                        Thread {
                            val r = Updater.check(ctx)
                            if (r != null) { upd = r; msg = "" } else msg = "Yeni sürüm bulunamadı."
                        }.start()
                    }) { Text("Güncellemeyi kontrol et") }
                    TextButton(onClick = { onLogout() }) { Text("Çıkış yap") }
                    if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.labelMedium)
                }
                1 -> {
                    MonthBar(month) { month = it }
                    if (mtx.isEmpty()) Text("Bu ay işlem yok. + ile ekleyebilirsin.")
                    LazyColumn(Modifier.weight(1f)) {
                        items(mtx, key = { it.id }) { t ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.cat + if (t.note.isNotBlank()) " · " + t.note else "", fontWeight = FontWeight.Bold)
                                        Text(t.date, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Text(
                                        (if (t.income) "+" else "−") + fmt(t.amount),
                                        color = if (t.income) GREEN else RED, fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { setTx(txs.filter { it.id != t.id }) }) { Text("Sil") }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Text("Aylık ödemeler", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                    if (pays.isEmpty()) Text("Kira, fatura gibi düzenli ödemelerini + ile ekle. Vadesi yaklaşınca bildirim gelir.")
                    LazyColumn(Modifier.weight(1f)) {
                        items(pays.sortedBy { it.day }, key = { it.id }) { p ->
                            val paid = nowS in p.paid
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(p.name, fontWeight = FontWeight.Bold)
                                        Text(fmt(p.amount), fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        "Her ayın ${p.day}. günü · " + if (paid) "bu ay ödendi ✓" else daysText(daysLeft(p)),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Row {
                                        TextButton(onClick = { toggle(p) }) { Text(if (paid) "Geri al" else "Ödendi") }
                                        TextButton(onClick = { setPay(pays.filter { it.id != p.id }) }) { Text("Sil") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    upd?.let { u ->
        AlertDialog(
            onDismissRequest = { if (prog < 0) upd = null },
            title = { Text("Güncelleme var") },
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

    if (dlg) {
        if (tab == 2) {
            PayDialog({ dlg = false }) { setPay(pays + it); dlg = false }
        } else {
            TxDialog({ dlg = false }) {
                setTx(txs + it)
                month = YearMonth.from(LocalDate.parse(it.date))
                dlg = false
            }
        }
    }
}

@Composable
fun MonthBar(m: YearMonth, set: (YearMonth) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { set(m.minusMonths(1)) }) { Text("‹") }
        Text(
            m.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("tr")) + " " + m.year,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = { set(m.plusMonths(1)) }) { Text("›") }
    }
}

@Composable
fun Stat(label: String, v: Double, color: Color) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(fmt(v), style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TxDialog(onDismiss: () -> Unit, onSave: (Tx) -> Unit) {
    var income by remember { mutableStateOf(false) }
    var amt by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(CG[0]) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    val cats = if (income) CI else CG
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni işlem") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    FilterChip(selected = !income, onClick = { income = false; cat = CG[0] }, label = { Text("Gider") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = income, onClick = { income = true; cat = CI[0] }, label = { Text("Gelir") })
                }
                OutlinedTextField(
                    amt, { amt = it }, label = { Text("Tutar (₺)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    cats.forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) })
                        Spacer(Modifier.width(6.dp))
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Tarih (YYYY-AA-GG)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Not") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amt.replace(',', '.').toDoubleOrNull()
                val d = try { LocalDate.parse(date) } catch (e: Exception) { null }
                if (a != null && a > 0 && d != null) onSave(Tx(newId(), income, a, cat, d.toString(), note.trim()))
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun PayDialog(onDismiss: () -> Unit, onSave: (Pay) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni aylık ödeme") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Ad (kira, internet...)") }, singleLine = true)
                OutlinedTextField(
                    amt, { amt = it }, label = { Text("Tutar (₺)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    day, { day = it }, label = { Text("Ayın kaçıncı günü (1-31)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amt.replace(',', '.').toDoubleOrNull()
                val d = day.toIntOrNull()
                if (name.isNotBlank() && a != null && a > 0 && d != null && d in 1..31)
                    onSave(Pay(newId(), name.trim(), a, d))
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

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
