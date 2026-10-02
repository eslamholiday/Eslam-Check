package com.eslam.check.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eslam.check.MainViewModel
import com.eslam.check.data.*
import com.eslam.check.util.CommissionEngine
import kotlin.math.roundToInt

private enum class MainTab(val title: String) {
    HOME("الرئيسية"),
    REVIEW("المراجعة"),
    PASSENGERS("المسافرون"),
    PAYMENTS("التسديدات"),
    MORE("المزيد")
}

@Composable
fun EslamCheckApp(vm: MainViewModel) {
    EslamCheckTheme {
        val message by vm.message.collectAsState()
        val busy by vm.busy.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        var tab by remember { mutableStateOf(MainTab.HOME) }
        var manualOpen by remember { mutableStateOf(false) }
        var detailId by remember { mutableStateOf<String?>(null) }
        var calculatorOpen by remember { mutableStateOf(false) }

        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                vm.clearMessage()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    listOf(
                        Triple(MainTab.HOME, Icons.Rounded.Home, "الرئيسية"),
                        Triple(MainTab.REVIEW, Icons.Rounded.FactCheck, "المراجعة"),
                        Triple(MainTab.PASSENGERS, Icons.Rounded.Groups, "المسافرون"),
                        Triple(MainTab.PAYMENTS, Icons.Rounded.Payments, "التسديدات"),
                        Triple(MainTab.MORE, Icons.Rounded.MoreHoriz, "المزيد")
                    ).forEach { item ->
                        NavigationBarItem(
                            selected = tab == item.first,
                            onClick = { tab = item.first },
                            icon = { Icon(item.second, item.third) },
                            label = { Text(item.third) }
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { manualOpen = true }) {
                    Icon(Icons.Rounded.Add, "إضافة")
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    MainTab.HOME -> DashboardScreen(vm, { tab = MainTab.REVIEW }) { detailId = it }
                    MainTab.REVIEW -> ReviewScreen(vm) { detailId = it }
                    MainTab.PASSENGERS -> PassengersScreen(vm)
                    MainTab.PAYMENTS -> PaymentsScreen(vm) { detailId = it }
                    MainTab.MORE -> MoreScreen(vm)
                }

                CalculatorBubble(
                    opened = calculatorOpen,
                    onToggle = { calculatorOpen = !calculatorOpen },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 82.dp)
                )

                if (busy) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp) {
                            Row(
                                Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                                Text("جاري قراءة الكشف ومقارنة العمليات...")
                            }
                        }
                    }
                }
            }
        }

        if (manualOpen) {
            ManualTransactionDialog(vm) { manualOpen = false }
        }
        detailId?.let { id ->
            TransactionDetailDialog(vm, id, onDismiss = { detailId = null }) { next ->
                detailId = next
            }
        }
    }
}

@Composable
private fun DashboardScreen(vm: MainViewModel, onOpenReview: () -> Unit, onDetail: (String) -> Unit) {
    val stats by vm.stats.collectAsState()
    val txs by vm.transactions.collectAsState()
    var importCurrency by remember { mutableStateOf<Currency?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri, importCurrency)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Eslam Check", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Travel • Review • Finance", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("غير مراجع", stats.unreviewed, Modifier.weight(1f), onOpenReview)
                StatCard("مبهم", stats.ambiguous, Modifier.weight(1f), onOpenReview)
                StatCard("تغيّر", stats.changed, Modifier.weight(1f), onOpenReview)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("استيراد كشف Best Choice", fontWeight = FontWeight.Bold)
                    Text("ارفع كشف الدينار أو الدولار بشكل منفصل.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            importCurrency = Currency.IQD
                            launcher.launch(arrayOf("application/pdf"))
                        }) {
                            Icon(Icons.Rounded.UploadFile, null)
                            Spacer(Modifier.width(5.dp))
                            Text("دينار")
                        }
                        OutlinedButton(onClick = {
                            importCurrency = Currency.USD
                            launcher.launch(arrayOf("application/pdf"))
                        }) {
                            Icon(Icons.Rounded.UploadFile, null)
                            Spacer(Modifier.width(5.dp))
                            Text("دولار")
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("آخر العمليات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenReview) { Text("عرض الكل") }
            }
        }
        items(txs.take(8), key = { it.id }) { tx ->
            TransactionCard(tx, onDetail = { onDetail(tx.id) }, onReview = { vm.setReview(tx.id, ReviewState.REVIEWED) })
        }
    }
}

@Composable
private fun StatCard(title: String, value: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewScreen(vm: MainViewModel, onDetail: (String) -> Unit) {
    val all by vm.transactions.collectAsState()
    var search by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TxType?>(null) }
    var onlyOpen by remember { mutableStateOf(true) }

    val filtered = remember(all, search, typeFilter, onlyOpen) {
        all.filter { tx ->
            (typeFilter == null || tx.type == typeFilter) &&
                (!onlyOpen || tx.reviewState != ReviewState.REVIEWED) &&
                (search.isBlank() ||
                    tx.pnr.orEmpty().contains(search, true) ||
                    tx.operationNo.orEmpty().contains(search, true) ||
                    tx.airline.orEmpty().contains(search, true) ||
                    tx.note.orEmpty().contains(search, true))
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("المراجعة", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            label = { Text("PNR / رقم العملية / شركة") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = onlyOpen, onClick = { onlyOpen = !onlyOpen }, label = { Text("غير المراجع") })
            FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("الكل") })
            FilterChip(selected = typeFilter == TxType.TICKET, onClick = { typeFilter = TxType.TICKET }, label = { Text("تذاكر") })
            FilterChip(selected = typeFilter == TxType.VISA, onClick = { typeFilter = TxType.VISA }, label = { Text("فيز") })
        }
        Text("${filtered.size} عملية", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { tx ->
                TransactionCard(tx, onDetail = { onDetail(tx.id) }, onReview = { vm.setReview(tx.id, ReviewState.REVIEWED) })
            }
        }
    }
}

@Composable
private fun TransactionCard(tx: Transaction, onDetail: () -> Unit, onReview: () -> Unit) {
    val accent = when {
        tx.changedAfterReview -> Warn
        tx.type == TxType.UNKNOWN -> Mystery
        tx.reviewState == ReviewState.REVIEWED -> Good
        tx.reviewState == ReviewState.FOLLOW_UP -> Warn
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDetail),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Row {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tx.pnr ?: labelFor(tx.type), fontWeight = FontWeight.Bold)
                    Text(statusFor(tx), fontSize = 11.sp, color = accent)
                }
                Text(
                    "${labelFor(tx.type)} • ${tx.currency.name}${tx.operationNo?.let { " • #$it" } ?: ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMoney(tx.amount, tx.currency), fontWeight = FontWeight.SemiBold)
                    if (tx.discount != 0.0) Text("Discount ${formatMoney(tx.discount, tx.currency)}", fontSize = 12.sp)
                }
                tx.warning?.let { Text(it, color = Warn, fontSize = 12.sp) }
            }
            if (tx.reviewState != ReviewState.REVIEWED) {
                IconButton(onClick = onReview, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Icon(Icons.Rounded.CheckCircleOutline, "تمت المراجعة", tint = Good)
                }
            }
        }
    }
}

@Composable
private fun PassengersScreen(vm: MainViewModel) {
    val passengers by vm.passengers.collectAsState()
    var search by remember { mutableStateOf("") }
    val filtered = passengers.filter {
        search.isBlank() || it.name.contains(search, true) || it.passport.orEmpty().contains(search, true) || it.id.contains(search, true)
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("المسافرون", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            search, { search = it }, Modifier.fillMaxWidth(),
            label = { Text("بحث بالاسم / الجواز / ID") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { p ->
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Text(p.name.take(2).uppercase(), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text("ID: ${p.id.take(8)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            p.passport?.let { Text("جواز: $it", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsScreen(vm: MainViewModel, onDetail: (String) -> Unit) {
    val all by vm.transactions.collectAsState()
    val payments = all.filter { it.type == TxType.PAYMENT }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("التسديدات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("المطابقة تقريبية والتأكيد النهائي يدوي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(payments, key = { it.id }) { tx ->
                TransactionCard(tx, onDetail = { onDetail(tx.id) }, onReview = { vm.setReview(tx.id, ReviewState.REVIEWED) })
            }
        }
    }
}

@Composable
private fun MoreScreen(vm: MainViewModel) {
    val rules by vm.rules.collectAsState()
    var issuer by remember { mutableStateOf(vm.setting("issuer_whatsapp", "")) }
    var usdTolerance by remember { mutableStateOf(vm.setting("usd_tolerance", "1.0")) }
    var iqdTolerance by remember { mutableStateOf(vm.setting("iqd_tolerance", "1000")) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("المزيد والإعدادات", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("واتساب جهة الإصدار", fontWeight = FontWeight.Bold)
                    OutlinedTextField(issuer, { issuer = it }, Modifier.fillMaxWidth(), label = { Text("رقم مع رمز الدولة") }, singleLine = true)
                    Button(onClick = { vm.setSetting("issuer_whatsapp", issuer) }) { Text("حفظ") }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هامش فرق الحساب", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(usdTolerance, { usdTolerance = it }, Modifier.weight(1f), label = { Text("USD") })
                        OutlinedTextField(iqdTolerance, { iqdTolerance = it }, Modifier.weight(1f), label = { Text("IQD") })
                    }
                    Button(onClick = {
                        vm.setSetting("usd_tolerance", usdTolerance)
                        vm.setSetting("iqd_tolerance", iqdTolerance)
                    }) { Text("حفظ") }
                }
            }
        }
        item { Text("قواعد العمولات", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(rules, key = { it.id }) { rule ->
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(rule.airline, fontWeight = FontWeight.Bold)
                    Text(ruleLabel(rule), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text(
                "الأدوات المتقدمة مثل التصدير والنسخ الاحتياطي ووضع الاختبار وسجل التعلم ستبقى تحت هذا القسم بدون ازدحام الواجهة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManualTransactionDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(TxType.TICKET) }
    var currency by remember { mutableStateOf(Currency.USD) }
    var pnr by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }
    var baseFare by remember { mutableStateOf("") }
    var referenceTotal by remember { mutableStateOf("") }
    var airline by remember { mutableStateOf("") }
    var passenger by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("إضافة عملية", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "إغلاق") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(TxType.TICKET, TxType.VISA, TxType.CHANGE, TxType.REFUND, TxType.PAYMENT, TxType.UNKNOWN).forEach {
                                FilterChip(selected = type == it, onClick = { type = it }, label = { Text(labelFor(it)) })
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Currency.entries.forEach {
                                FilterChip(selected = currency == it, onClick = { currency = it }, label = { Text(it.name) })
                            }
                        }
                    }
                    if (type != TxType.PAYMENT) {
                        item { OutlinedTextField(pnr, { pnr = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("PNR") }, singleLine = true) }
                        item { OutlinedTextField(route, { route = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Route") }, singleLine = true) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(amount, { amount = it }, Modifier.weight(1f), label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(discount, { discount = it }, Modifier.weight(1f), label = { Text("Discount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                    if (type == TxType.TICKET) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(baseFare, { baseFare = it }, Modifier.weight(1f), label = { Text("Base Fare") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                OutlinedTextField(referenceTotal, { referenceTotal = it }, Modifier.weight(1f), label = { Text("Total المصدر") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                        }
                        item { OutlinedTextField(airline, { airline = it }, Modifier.fillMaxWidth(), label = { Text("شركة الطيران") }, singleLine = true) }
                    }
                    if (type != TxType.PAYMENT) {
                        item { OutlinedTextField(passenger, { passenger = it }, Modifier.fillMaxWidth(), label = { Text("اسم المسافر") }) }
                    }
                    item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة داخلية") }, minLines = 2) }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.addManual(
                            type = type,
                            currency = currency,
                            pnr = pnr.ifBlank { null },
                            route = route.ifBlank { null },
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            discount = discount.toDoubleOrNull() ?: 0.0,
                            baseFare = baseFare.toDoubleOrNull(),
                            referenceTotal = referenceTotal.toDoubleOrNull(),
                            airline = airline.ifBlank { null },
                            passengerNames = listOfNotNull(passenger.trim().takeIf { it.isNotBlank() }),
                            note = note.ifBlank { null }
                        )
                        onDismiss()
                    }
                ) { Text("حفظ العملية") }
            }
        }
    }
}

@Composable
private fun TransactionDetailDialog(vm: MainViewModel, id: String, onDismiss: () -> Unit, onNext: (String) -> Unit) {
    val all by vm.transactions.collectAsState()
    val rules by vm.rules.collectAsState()
    val tx = all.firstOrNull { it.id == id } ?: vm.transaction(id) ?: return
    val passengers = remember(id, all) { vm.passengersFor(id) }
    val context = LocalContext.current
    var edit by remember(id) { mutableStateOf(tx) }
    val rule = rules.firstOrNull { it.airline.equals(edit.airline, true) }
    val tolerance = if (edit.currency == Currency.USD) {
        vm.setting("usd_tolerance", "1").toDoubleOrNull() ?: 1.0
    } else {
        vm.setting("iqd_tolerance", "1000").toDoubleOrNull() ?: 1000.0
    }
    val commission = CommissionEngine.calculate(edit.baseFare, edit.discount, passengers.size, rule, tolerance)
    val next = all.firstOrNull { it.reviewState != ReviewState.REVIEWED && it.id != id }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.93f), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(edit.pnr ?: labelFor(edit.type), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("${labelFor(edit.type)} • ${edit.currency.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "إغلاق") }
                }

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("المسافرون: " + if (passengers.isEmpty()) "غير محدد" else passengers.joinToString(" • ") { it.name }) }
                    item {
                        OutlinedTextField(
                            edit.airline.orEmpty(),
                            { edit = edit.copy(airline = it) },
                            Modifier.fillMaxWidth(),
                            label = { Text("شركة الطيران") },
                            singleLine = true
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditableNumber("المبلغ", edit.amount, Modifier.weight(1f)) { edit = edit.copy(amount = it) }
                            EditableNumber("Discount", edit.discount, Modifier.weight(1f)) { edit = edit.copy(discount = it) }
                        }
                    }
                    item {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("مراجعة العمولة", fontWeight = FontWeight.Bold)
                                if (rule == null) {
                                    Text("لا توجد قاعدة لهذه الشركة. يمكنك تعديلها من الإعدادات.")
                                } else {
                                    Text(ruleLabel(rule))
                                    commission.expected?.let { Text("المتوقع: ${formatMoney(it, edit.currency)}") }
                                    commission.difference?.let { Text("الفرق: ${formatMoney(it, edit.currency)}") }
                                    Text(commission.explanation, color = if (commission.isWithinTolerance) Good else Warn)
                                }
                            }
                        }
                    }
                    edit.warning?.let { item { Text(it, color = Warn) } }
                    edit.rawText?.let { raw ->
                        item {
                            Text("النص الأصلي من PDF", fontWeight = FontWeight.Bold)
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                                Text(raw, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            edit.note.orEmpty(),
                            { edit = edit.copy(note = it) },
                            Modifier.fillMaxWidth(),
                            label = { Text("ملاحظة داخلية") },
                            minLines = 2
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val phone = vm.setting("issuer_whatsapp", "").filter(Char::isDigit)
                            if (phone.isNotBlank()) {
                                val text = Uri.encode("PNR ${edit.pnr.orEmpty()}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$text")))
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Chat, null)
                        Spacer(Modifier.width(4.dp))
                        Text("جهة الإصدار")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setReview(edit.id, ReviewState.FOLLOW_UP) }
                    ) { Text("متابعة") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vm.updateTransaction(edit)
                            vm.setReview(edit.id, ReviewState.REVIEWED)
                            if (next != null) onNext(next.id) else onDismiss()
                        }
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text("صح ثم التالي")
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { vm.updateTransaction(edit) }
                    ) { Text("حفظ") }
                }
            }
        }
    }
}

@Composable
private fun EditableNumber(label: String, value: Double, modifier: Modifier, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == 0.0) "" else value.toString()) }
    OutlinedTextField(
        text,
        {
            text = it
            it.toDoubleOrNull()?.let(onChange)
        },
        modifier,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
private fun CalculatorBubble(opened: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var op by remember { mutableStateOf("+") }

    val result = remember(a, b, op) {
        val x = a.toDoubleOrNull()
        val y = b.toDoubleOrNull()
        if (x == null || y == null) null else when (op) {
            "+" -> x + y
            "−" -> x - y
            "×" -> x * y
            "÷" -> if (y == 0.0) null else x / y
            "%" -> x * y / 100.0
            else -> null
        }
    }

    Box(
        modifier.offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    drag += amount
                }
            }
    ) {
        if (!opened) {
            SmallFloatingActionButton(onClick = onToggle) {
                Icon(Icons.Rounded.Calculate, "الحاسبة")
            }
        } else {
            Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp) {
                Column(Modifier.width(250.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("حاسبة", fontWeight = FontWeight.Bold)
                        IconButton(onClick = onToggle) { Icon(Icons.Rounded.Close, "تصغير") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(a, { a = it }, Modifier.weight(1f), label = { Text("A") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(b, { b = it }, Modifier.weight(1f), label = { Text("B") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("+", "−", "×", "÷", "%").forEach { symbol ->
                            FilterChip(selected = op == symbol, onClick = { op = symbol }, label = { Text(symbol) })
                        }
                    }
                    Text("النتيجة: ${result?.let { String.format("%.2f", it) } ?: "—"}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun labelFor(type: TxType): String = when (type) {
    TxType.TICKET -> "تذكرة"
    TxType.VISA -> "فيزا"
    TxType.CHANGE -> "تغيير"
    TxType.REFUND -> "Refund"
    TxType.PAYMENT -> "تسديد"
    TxType.VOID -> "إلغاء"
    TxType.REISSUE -> "Reissue"
    TxType.FEE -> "رسم"
    TxType.UNKNOWN -> "مبهم"
}

private fun statusFor(tx: Transaction): String = when {
    tx.changedAfterReview -> "تغيّرت"
    tx.type == TxType.UNKNOWN -> "مبهم"
    tx.reviewState == ReviewState.REVIEWED -> "مراجع"
    tx.reviewState == ReviewState.FOLLOW_UP -> "متابعة"
    else -> "جديد"
}

private fun ruleLabel(rule: CommissionRule): String = when (rule.kind) {
    RuleKind.PERCENT_BASE -> "${rule.value}% من Base Fare"
    RuleKind.FIXED_PER_PASSENGER -> "+${rule.value} لكل مسافر"
    RuleKind.PRIVATE_MANUAL -> "عمولة خاصة"
    RuleKind.NONE -> "بدون عمولة"
}

private fun formatMoney(value: Double, currency: Currency): String =
    if (currency == Currency.USD) "$" + String.format("%,.2f", value)
    else String.format("%,.0f د.ع", value)
