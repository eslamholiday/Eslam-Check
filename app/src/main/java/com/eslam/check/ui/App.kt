package com.eslam.check.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
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
    val settingsRevision by vm.settingsRevision.collectAsState()
    val preset = remember(settingsRevision) { vm.setting("theme_preset", "NAVY") }
    val primary = remember(settingsRevision) { vm.setting("theme_primary", "") }
    val background = remember(settingsRevision) { vm.setting("theme_background", "") }
    val surface = remember(settingsRevision) { vm.setting("theme_surface", "") }
    val textColor = remember(settingsRevision) { vm.setting("theme_text", "") }
    val fontChoice = remember(settingsRevision) { vm.setting("font_choice", "SANS") }
    val fontScale = remember(settingsRevision) { vm.setting("font_scale", "1.0").toFloatOrNull() ?: 1f }

    EslamCheckTheme(
        preset = preset,
        customPrimary = primary,
        customBackground = background,
        customSurface = surface,
        customText = textColor,
        fontChoice = fontChoice,
        fontScale = fontScale
    ) {
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
                    MainTab.PASSENGERS -> PassengersScreen(vm) { detailId = it }
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
            key(id) {
                TransactionDetailDialog(vm, id, onDismiss = { detailId = null }) { next ->
                    detailId = next
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(vm: MainViewModel, onOpenReview: () -> Unit, onDetail: (String) -> Unit) {
    val stats by vm.stats.collectAsState()
    val txs by vm.transactions.collectAsState()
    var bridgeOpen by remember { mutableStateOf(false) }
    var showPdfExperimental by remember { mutableStateOf(false) }
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Hub, null, tint = MaterialTheme.colorScheme.secondary)
                        Column {
                            Text("Eslam Bridge", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("الصق نص ECX الذي أرسله لك ChatGPT. يقبل الدولار والدينار معًا.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { bridgeOpen = true }
                    ) {
                        Icon(Icons.Rounded.ContentPaste, null)
                        Spacer(Modifier.width(6.dp))
                        Text("لصق واستيراد نص ECX")
                    }
                    if (vm.setting("bridge_pdf_experimental", "false").toBoolean()) {
                        TextButton(onClick = { showPdfExperimental = !showPdfExperimental }) {
                            Text(if (showPdfExperimental) "إخفاء استيراد PDF التجريبي" else "استيراد PDF تجريبي")
                        }
                        if (showPdfExperimental) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    importCurrency = Currency.IQD
                                    launcher.launch(arrayOf("application/pdf"))
                                }) { Text("PDF دينار") }
                                OutlinedButton(onClick = {
                                    importCurrency = Currency.USD
                                    launcher.launch(arrayOf("application/pdf"))
                                }) { Text("PDF دولار") }
                            }
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
            TransactionCard(vm, tx, onDetail = { onDetail(tx.id) }, onReview = { vm.setReview(tx.id, ReviewState.REVIEWED) })
        }
    }

    if (bridgeOpen) {
        BridgeImportDialog(vm) { bridgeOpen = false }
    }
}

@Composable
private fun BridgeImportDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<BridgeParseResult?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Eslam Bridge", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("ECX v2 • هوية ثابتة للكشف والعملية", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "إغلاق") }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        preview = null
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("الصق النص هنا") },
                    placeholder = { Text("ECX|2\nB|SNAPSHOT|BESTCHOICE|CUMULATIVE|DOC_ID\nL|LEDGER_ID|USD|...\nT|OP_ID|USD|...") }
                )

                preview?.let { p ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (p.canImport) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (p.canImport) "النص صالح للاستيراد ✓" else "يوجد خطأ في النص",
                                fontWeight = FontWeight.Bold
                            )
                            Text("Snapshot: " + p.snapshotId.ifBlank { p.batchId.ifBlank { "بدون معرف" } })
                            Text("Document: " + p.documentId.ifBlank { "ECX v1 / غير محدد" }, fontSize = 12.sp)
                            Text("USD: " + p.usdCount + " • IQD: " + p.iqdCount + " • العمليات: " + p.transactions.size)
                            Text("PNR: " + p.pnrCount + " • المسافرون: " + p.passengerCount + " • خطوط مبهمة: " + p.ambiguousAirlines, fontSize = 12.sp)
                            Text(
                                "تذاكر " + p.transactions.count { it.type == TxType.TICKET } +
                                    " • فيز " + p.transactions.count { it.type == TxType.VISA } +
                                    " • تغيير " + p.transactions.count { it.type == TxType.CHANGE } +
                                    " • استرجاع " + p.transactions.count { it.type == TxType.REFUND } +
                                    " • تسديد " + p.transactions.count { it.type == TxType.PAYMENT } +
                                    " • Void " + p.transactions.count { it.type == TxType.VOID },
                                fontSize = 12.sp
                            )
                            p.ledgers.forEach { ledger ->
                                Text(
                                    ledger.currency.name + "  " + ledger.rangeFrom + " → " + ledger.rangeTo +
                                        "  |  Open " + ledger.openingBalance + "  |  Close " + ledger.closingBalance,
                                    fontSize = 12.sp
                                )
                            }
                            p.errors.take(4).forEach { Text("• " + it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                            p.warnings.take(4).forEach { Text("• " + it, color = Warn, fontSize = 12.sp) }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = text.isNotBlank(),
                        onClick = { preview = vm.previewBridge(text) }
                    ) {
                        Icon(Icons.Rounded.FactCheck, null)
                        Spacer(Modifier.width(4.dp))
                        Text("فحص النص")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = preview?.canImport == true,
                        onClick = {
                            preview?.let(vm::importBridge)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Rounded.DownloadDone, null)
                        Spacer(Modifier.width(4.dp))
                        Text("اعتماد")
                    }
                }
            }
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
    var typeFilter by remember { mutableStateOf<Set<TxType>>(emptySet()) }
    var statusFilter by remember { mutableStateOf("OPEN") }

    LaunchedEffect(search) { vm.refresh(search = search) }
    DisposableEffect(Unit) {
        onDispose { vm.refresh() }
    }

    val filtered = remember(all, typeFilter, statusFilter) {
        all.filter { tx ->
            (typeFilter.isEmpty() || tx.type in typeFilter) &&
                when (statusFilter) {
                    "OPEN" -> tx.reviewState != ReviewState.REVIEWED
                    "AMBIG" -> tx.type == TxType.UNKNOWN || (tx.type == TxType.TICKET && tx.currency == Currency.USD && tx.airline.isNullOrBlank())
                    "CHANGED" -> tx.changedAfterReview
                    "FOLLOW" -> tx.reviewState == ReviewState.FOLLOW_UP
                    "REVIEWED" -> tx.reviewState == ReviewState.REVIEWED
                    else -> true
                }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("المراجعة والتصنيف", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            label = { Text("PNR / عملية / مسافر / جواز / تذكرة / خط") },
            singleLine = true
        )

        Text("نوع العملية", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = typeFilter.isEmpty(),
                onClick = { typeFilter = emptySet() },
                label = { Text("الكل") }
            )
            listOf(
                TxType.TICKET to "تذاكر",
                TxType.VISA to "فيز",
                TxType.CHANGE to "تغيير",
                TxType.REFUND to "استرجاع",
                TxType.PAYMENT to "تسديد",
                TxType.VOID to "Void"
            ).forEach { (type, label) ->
                FilterChip(
                    selected = type in typeFilter,
                    onClick = {
                        typeFilter = if (type in typeFilter) typeFilter - type else typeFilter + type
                    },
                    label = { Text(label) }
                )
            }
        }

        Text("حالة التدقيق", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "OPEN" to "غير المراجع",
                "AMBIG" to "مبهم/خط غير محدد",
                "CHANGED" to "تغيّر",
                "FOLLOW" to "متابعة",
                "REVIEWED" to "مراجع",
                "ALL" to "الكل"
            ).forEach { (key, label) ->
                FilterChip(selected = statusFilter == key, onClick = { statusFilter = key }, label = { Text(label) })
            }
        }

        Text("${filtered.size} عملية", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { tx ->
                TransactionCard(vm, tx, onDetail = { onDetail(tx.id) }, onReview = { vm.setReview(tx.id, ReviewState.REVIEWED) })
            }
        }
    }
}

@Composable
private fun TransactionCard(vm: MainViewModel, tx: Transaction, onDetail: () -> Unit, onReview: () -> Unit) {
    val airlineMissing = tx.type == TxType.TICKET && tx.currency == Currency.USD && tx.airline.isNullOrBlank()
    val typeAccent = operationTypeColor(vm, tx.type)
    val accent = when {
        tx.changedAfterReview -> Warn
        tx.type == TxType.UNKNOWN || airlineMissing -> Mystery
        tx.reviewState == ReviewState.REVIEWED -> Good
        tx.reviewState == ReviewState.FOLLOW_UP -> Warn
        else -> typeAccent
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
                when {
                    tx.type == TxType.TICKET -> Text(
                        "الخط: " + (tx.airline ?: "مبهم — اختر من داخل PNR"),
                        fontSize = 12.sp,
                        color = if (airlineMissing) Mystery else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    tx.type == TxType.VISA || (tx.type == TxType.VOID && tx.visaCountry != null) ->
                        Text("الفيزا: " + (tx.visaCountry ?: "غير محددة"), fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMoney(tx.amount, tx.currency), fontWeight = FontWeight.SemiBold)
                    if (tx.type == TxType.TICKET && tx.discount != 0.0) Text("Discount ${formatMoney(tx.discount, tx.currency)}", fontSize = 12.sp)
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
private fun PassengersScreen(vm: MainViewModel, onDetail: (String) -> Unit) {
    val passengers by vm.passengers.collectAsState()
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PassengerCategory.ALL) }
    var selected by remember { mutableStateOf<Passenger?>(null) }

    val filtered = remember(passengers, search, category) {
        passengers.filter { p ->
            val matches = search.isBlank() ||
                p.name.contains(search, true) ||
                p.passport.orEmpty().contains(search, true) ||
                p.phone.orEmpty().contains(search, true) ||
                p.id.contains(search, true)
            val classOk = when (category) {
                PassengerCategory.ALL -> true
                PassengerCategory.RESPONSIBLE -> p.isResponsible
                PassengerCategory.DEPENDENT -> p.responsibleId != null
                PassengerCategory.INDEPENDENT -> !p.isResponsible && p.responsibleId == null
            }
            matches && classOk
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("المسافرون", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("كل اسم ملف مستقل: عملياته، المسؤول، التابعون، واتساب وملفات الجواز.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        OutlinedTextField(
            search, { search = it }, Modifier.fillMaxWidth(),
            label = { Text("بحث بالاسم / الهاتف / الجواز الداخلي / ID") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                PassengerCategory.ALL to "الجميع",
                PassengerCategory.RESPONSIBLE to "المسؤولون",
                PassengerCategory.DEPENDENT to "التابعون",
                PassengerCategory.INDEPENDENT to "المستقلون"
            ).forEach { (key, label) ->
                FilterChip(selected = category == key, onClick = { category = key }, label = { Text(label) })
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { p ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { selected = p },
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Text(p.name.take(2).uppercase(), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    p.isResponsible -> "مسؤول • " + vm.dependentsOf(p.id).size + " تابع"
                                    p.responsibleId != null -> "تابع"
                                    else -> "مستقل"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            p.phone?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            p.responsibleId?.let { rid ->
                                vm.passengerById(rid)?.let { Text("المسؤول: " + it.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary) }
                            }
                        }
                        Icon(Icons.Rounded.ChevronLeft, null)
                    }
                }
            }
        }
    }

    selected?.let { p ->
        key(p.id) {
            PassengerDetailDialog(
                vm = vm,
                passenger = p,
                allPassengers = passengers,
                onDismiss = { selected = null },
                onOpenTransaction = {
                    selected = null
                    onDetail(it)
                },
                onOpenPassenger = { selected = it }
            )
        }
    }
}

@Composable
private fun PassengerDetailDialog(
    vm: MainViewModel,
    passenger: Passenger,
    allPassengers: List<Passenger>,
    onDismiss: () -> Unit,
    onOpenTransaction: (String) -> Unit = {},
    onOpenPassenger: (Passenger) -> Unit = {}
) {
    val context = LocalContext.current
    var edit by remember(passenger.id, passenger.phone, passenger.isResponsible, passenger.responsibleId) {
        mutableStateOf(passenger)
    }
    var responsiblePicker by remember { mutableStateOf(false) }
    var dependentPicker by remember { mutableStateOf(false) }
    var files by remember(passenger.id) { mutableStateOf(vm.passengerFiles(passenger.id)) }
    val dependents = allPassengers.filter { it.responsibleId == passenger.id }
    val currentResponsible = edit.responsibleId?.let { id -> allPassengers.firstOrNull { it.id == id } }
    val personalOps = remember(passenger.id, vm.transactions.collectAsState().value) { vm.transactionsForPassenger(passenger.id) }
    val dependentOps = remember(passenger.id, allPassengers) { if (passenger.isResponsible) vm.transactionsForResponsible(passenger.id) else emptyList() }

    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val name = if (nameIndex >= 0) c.getString(nameIndex) else null
                        val number = if (numberIndex >= 0) c.getString(numberIndex) else null
                        edit = edit.copy(
                            name = name?.takeIf { it.isNotBlank() } ?: edit.name,
                            phone = number?.takeIf { it.isNotBlank() } ?: edit.phone
                        )
                    }
                }
            }
        }
    }

    val passportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val added = mutableListOf<PassengerFile>()
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            var displayName: String? = null
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) displayName = c.getString(idx)
                }
            }
            val mime = context.contentResolver.getType(uri)
            added += vm.addPassengerFileImmediate(passenger.id, uri.toString(), mime, displayName)
        }
        files = vm.passengerFiles(passenger.id)
        if (added.isNotEmpty()) Toast.makeText(context, "تمت إضافة ملفات الجواز", Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.94f), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(edit.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                edit.isResponsible -> "مسؤول"
                                edit.responsibleId != null -> "تابع"
                                else -> "مستقل"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "إغلاق") }
                }

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        OutlinedTextField(edit.name, { edit = edit.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("الاسم") })
                    }

                    item {
                        OutlinedTextField(
                            edit.phone.orEmpty(), { edit = edit.copy(phone = it) }, Modifier.fillMaxWidth(),
                            label = { Text("الهاتف / واتساب") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            trailingIcon = {
                                IconButton(onClick = {
                                    contactLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                                }) { Icon(Icons.Rounded.Contacts, "اختيار من جهات الاتصال") }
                            }
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { contactLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                            ) {
                                Icon(Icons.Rounded.ContactPhone, null)
                                Spacer(Modifier.width(5.dp))
                                Text("جهات الاتصال")
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = edit.phone.orEmpty().filter(Char::isDigit).isNotBlank(),
                                onClick = {
                                    val phone = edit.phone.orEmpty().filter(Char::isDigit)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone)))
                                }
                            ) {
                                Icon(Icons.Rounded.Chat, null)
                                Spacer(Modifier.width(5.dp))
                                Text("WhatsApp")
                            }
                        }
                    }

                    item {
                        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("ملفات الجواز", fontWeight = FontWeight.Bold)
                                        Text("صور أو PDF • يمكن تعيين ملف أساسي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { passportLauncher.launch(arrayOf("image/*", "application/pdf")) }) {
                                        Icon(Icons.Rounded.UploadFile, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("رفع")
                                    }
                                }
                                if (files.isEmpty()) {
                                    Text("لا توجد ملفات مرفوعة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    files.forEach { file ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (file.mimeType?.startsWith("image/") == true) Icons.Rounded.Image else Icons.Rounded.PictureAsPdf, null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(file.displayName ?: "ملف جواز", Modifier.weight(1f), maxLines = 1)
                                            if (file.isPrimary) AssistChip(onClick = {}, label = { Text("أساسي", fontSize = 10.sp) })
                                            IconButton(onClick = {
                                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(file.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                                catch (_: Exception) { Toast.makeText(context, "تعذر فتح الملف", Toast.LENGTH_SHORT).show() }
                                            }) { Icon(Icons.Rounded.OpenInNew, "فتح") }
                                            if (!file.isPrimary) {
                                                IconButton(onClick = {
                                                    vm.setPrimaryPassengerFileImmediate(passenger.id, file.id)
                                                    files = vm.passengerFiles(passenger.id)
                                                }) { Icon(Icons.Rounded.StarOutline, "تعيين أساسي") }
                                            }
                                            IconButton(onClick = {
                                                vm.deletePassengerFileImmediate(file.id)
                                                files = vm.passengerFiles(passenger.id)
                                            }) { Icon(Icons.Rounded.DeleteOutline, "حذف") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("تعيين كمسؤول", fontWeight = FontWeight.Bold)
                                    Text("يبقى ضمن جميع المسافرين ويضم التابعين تحته.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(edit.isResponsible, { edit = edit.copy(isResponsible = it) })
                            }
                        }
                    }

                    item {
                        OutlinedButton(onClick = { responsiblePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.SupervisorAccount, null)
                            Spacer(Modifier.width(6.dp))
                            Text("المسؤول الحالي: " + (currentResponsible?.name ?: "لا يوجد"))
                        }
                    }

                    if (edit.responsibleId != null) {
                        item {
                            OutlinedTextField(
                                edit.responsibleRelation.orEmpty(),
                                { edit = edit.copy(responsibleRelation = it) },
                                Modifier.fillMaxWidth(),
                                label = { Text("صلة العلاقة - اختياري") }
                            )
                        }
                    }

                    item {
                        Text("عمليات هذا الشخص (" + personalOps.size + ")", fontWeight = FontWeight.Bold)
                    }
                    if (personalOps.isEmpty()) {
                        item { Text("لا توجد عمليات مرتبطة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    } else {
                        items(personalOps.take(20), key = { "self-" + it.id }) { tx ->
                            PersonOperationRow(tx) { onOpenTransaction(tx.id) }
                        }
                    }

                    if (edit.isResponsible) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("المسافرون التابعون (" + dependents.size + ")", fontWeight = FontWeight.Bold)
                                TextButton(onClick = { dependentPicker = true }) {
                                    Icon(Icons.Rounded.GroupAdd, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("إضافة عدة")
                                }
                            }
                        }
                        items(dependents, key = { "dep-" + it.id }) { d ->
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(d.name, Modifier.weight(1f).clickable { onOpenPassenger(d) }, color = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { vm.assignResponsible(d.id, null) }) { Text("فك الربط") }
                                }
                            }
                        }

                        item { Text("عمليات التابعين (" + dependentOps.size + ")", fontWeight = FontWeight.Bold) }
                        items(dependentOps.take(30), key = { "dep-op-" + it.id }) { tx ->
                            PersonOperationRow(tx) { onOpenTransaction(tx.id) }
                        }
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.updatePassenger(edit)
                        if (edit.responsibleId != passenger.responsibleId || edit.responsibleRelation != passenger.responsibleRelation) {
                            vm.assignResponsible(edit.id, edit.responsibleId, edit.responsibleRelation)
                        }
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ")
                }
            }
        }
    }

    if (responsiblePicker) {
        AlertDialog(
            onDismissRequest = { responsiblePicker = false },
            title = { Text("اختيار المسؤول") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        TextButton(onClick = {
                            edit = edit.copy(responsibleId = null, responsibleRelation = null)
                            responsiblePicker = false
                        }) { Text("بدون مسؤول") }
                    }
                    items(allPassengers.filter { it.isResponsible && it.id != edit.id }, key = { it.id }) { p ->
                        TextButton(onClick = {
                            edit = edit.copy(responsibleId = p.id)
                            responsiblePicker = false
                        }) { Text(p.name) }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (dependentPicker) {
        BulkDependentPicker(
            allPassengers = allPassengers,
            responsible = edit,
            onDismiss = { dependentPicker = false },
            onConfirm = { ids ->
                ids.forEach { vm.assignResponsible(it, edit.id) }
                dependentPicker = false
            }
        )
    }
}

@Composable
private fun PersonOperationRow(tx: Transaction, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((tx.pnr ?: labelFor(tx.type)) + " • " + labelFor(tx.type), fontWeight = FontWeight.SemiBold)
                Text("#" + (tx.operationNo ?: "—") + " • " + formatMoney(tx.amount, tx.currency), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronLeft, null)
        }
    }
}

@Composable
private fun BulkDependentPicker(
    allPassengers: List<Passenger>,
    responsible: Passenger,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var onlyFree by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val visible = allPassengers.filter {
        it.id != responsible.id &&
            it.responsibleId != responsible.id &&
            (!onlyFree || it.responsibleId == null) &&
            (search.isBlank() || it.name.contains(search, true) || it.phone.orEmpty().contains(search, true) || it.passport.orEmpty().contains(search, true))
    }
    val movingCount = selected.count { id -> allPassengers.firstOrNull { it.id == id }?.responsibleId != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مسافرين تابعين") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    search, { search = it }, Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    label = { Text("بحث") },
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = onlyFree, onClick = { onlyFree = !onlyFree }, label = { Text("بدون مسؤول فقط") })
                    Text(selected.size.toString() + " محدد", color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { selected = selected + visible.map { it.id } }) { Text("تحديد الكل الظاهر") }
                    TextButton(onClick = { selected = emptySet() }) { Text("إلغاء التحديد") }
                }
                if (movingCount > 0) {
                    Text("تنبيه: " + movingCount + " من المحددين مرتبطون بمسؤول آخر وسيتم نقلهم.", color = Warn, fontSize = 12.sp)
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(visible, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (p.id in selected) selected - p.id else selected + p.id
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = p.id in selected,
                                onCheckedChange = { checked -> selected = if (checked) selected + p.id else selected - p.id }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(p.name)
                                if (p.responsibleId != null) Text("مرتبط بمسؤول آخر", fontSize = 11.sp, color = Warn)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected) }) {
                Text("إضافة المحددين (" + selected.size + ")")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
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
                TransactionCard(vm, tx, onDetail = { onDetail(tx.id) }, onReview = { vm.setReview(tx.id, ReviewState.REVIEWED) })
            }
        }
    }
}

@Composable
private fun MoreScreen(vm: MainViewModel) {
    var page by remember { mutableStateOf("root") }

    when (page) {
        "root" -> SettingsRoot { page = it }
        "bridge" -> BridgeSettings(vm) { page = "root" }
        "review" -> ReviewSettings(vm) { page = "root" }
        "commissions" -> CommissionSettings(vm) { page = "root" }
        "whatsapp" -> WhatsAppSettings(vm) { page = "root" }
        "people" -> InfoSettingsPage(
            title = "المسافرون والعملاء",
            items = listOf(
                "جميع المسافرين / المسؤولون / التابعون / المستقلون",
                "المسؤول يبقى مسافرًا بنفس ID ولا ينشأ سجل مكرر",
                "يمكن ربط عدة مسافرين بمسؤول واحد وتغيير المسؤول أو فك الربط",
                "رقم واتساب المسؤول يستخدم كزر الزبون عند توفره"
            ),
            onBack = { page = "root" }
        )
        "payments" -> InfoSettingsPage(
            title = "التسديدات",
            items = listOf(
                "مطابقة التسديدات تبقى تقريبية",
                "التأكيد النهائي يدوي",
                "يمكن ربط أكثر من إيصال بتسديد واحد",
                "التسديدات تظهر بقسم مستقل"
            ),
            onBack = { page = "root" }
        )
        "appearance" -> InfoSettingsPage(
            title = "الشكل والواجهة",
            items = listOf(
                "الوضع الداكن والفاتح يتبع النظام",
                "ألوان الحالات: صحيح / تحذير / مبهم",
                "الواجهة الأساسية تبقى خفيفة",
                "الإعدادات المتقدمة لا تظهر في الصفحة الرئيسية"
            ),
            onBack = { page = "root" }
        )
        "data" -> InfoSettingsPage(
            title = "البيانات والنسخ",
            items = listOf(
                "البيانات محلية على الهاتف",
                "ECX هو مصدر الاستيراد الأساسي",
                "العمليات القديمة لا يعاد إدخالها",
                "رقم العملية + العملة هما مفتاح المطابقة"
            ),
            onBack = { page = "root" }
        )
        "advanced" -> InfoSettingsPage(
            title = "متقدم",
            items = listOf(
                "ECX v2 هو بروتوكول Eslam Bridge الحالي مع دعم ECX v1 القديم",
                "لكل كشف DOC_ID/SNAPSHOT_ID ولكل عملية OP_ID ثابت عبر الكشوفات التراكمية",
                "Balance و Sequence معلومات Snapshot وليسا هوية للعملية",
                "Change وNew Change يترجمان إلى Change فقط؛ Refund وNew Refund إلى Refund فقط",
                "Sale Ticket صفر وفق القاعدة المتفق عليها يترجم Void، وVisa صفر تترجم ملغاة",
                "تذاكر كشف IQD تصنف Iraqi Airways تلقائيًا؛ خط USD غير المعروف يبقى مبهمًا داخل التذكرة"
            ),
            onBack = { page = "root" }
        )
    }
}

@Composable
private fun SettingsRoot(onOpen: (String) -> Unit) {
    val entries = listOf(
        Triple("bridge", "Eslam Bridge", Icons.Rounded.Hub),
        Triple("review", "المراجعة والكشوفات", Icons.Rounded.FactCheck),
        Triple("commissions", "التذاكر والعمولات", Icons.Rounded.Percent),
        Triple("people", "المسافرون والعملاء", Icons.Rounded.Groups),
        Triple("payments", "التسديدات", Icons.Rounded.Payments),
        Triple("whatsapp", "واتساب", Icons.Rounded.Chat),
        Triple("appearance", "الشكل والواجهة", Icons.Rounded.Palette),
        Triple("data", "البيانات والنسخ", Icons.Rounded.Storage),
        Triple("advanced", "متقدم", Icons.Rounded.Tune)
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("المزيد والإعدادات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("إعدادات متشعبة بدل صفحة طويلة واحدة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(entries) { e ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(e.first) },
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(e.third, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(e.second, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Rounded.ChevronLeft, null)
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowForward, "رجوع") }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BridgeSettings(vm: MainViewModel, onBack: () -> Unit) {
    var pdfExperimental by remember { mutableStateOf(vm.setting("bridge_pdf_experimental", "false").toBoolean()) }
    var rejectErrors by remember { mutableStateOf(vm.setting("bridge_reject_on_error", "true").toBoolean()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader("Eslam Bridge", onBack) }
        item {
            SettingsInfoCard("لغة الاستيراد", "ECX v2 • هوية ثابتة للملف والكشف والعملية • يدعم ECX v1 القديم")
        }
        item {
            SettingSwitchRow(
                "رفض النص عند وجود خطأ هيكلي",
                rejectErrors
            ) {
                rejectErrors = it
                vm.setSetting("bridge_reject_on_error", it.toString())
            }
        }
        item {
            SettingSwitchRow(
                "إظهار استيراد PDF التجريبي",
                pdfExperimental
            ) {
                pdfExperimental = it
                vm.setSetting("bridge_pdf_experimental", it.toString())
            }
        }
        item {
            SettingsInfoCard(
                "المطابقة",
                "ECX v2 يستخدم OP_ID ثابتًا. عند غيابه نرجع إلى رقم العملية + العملة. تغيّر Balance أو ترتيب الصفوف وحده لا يعتبر تغييرًا."
            )
        }
        item {
            SettingsInfoCard(
                "العمليات الصفرية",
                "حسب قواعدك: تذكرة Sale Tickets بقيمة صفر = Void، Visa بقيمة صفر = ملغاة، وSale Tickets بملاحظة «تغيير» = Change."
            )
        }
    }
}

@Composable
private fun ReviewSettings(vm: MainViewModel, onBack: () -> Unit) {
    var usdTolerance by remember { mutableStateOf(vm.setting("usd_tolerance", "1.0")) }
    var iqdTolerance by remember { mutableStateOf(vm.setting("iqd_tolerance", "1000")) }
    var reviewLock by remember { mutableStateOf(vm.setting("review_lock", "false").toBoolean()) }
    var pageSize by remember { mutableStateOf(vm.setting("page_size", "20")) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader("المراجعة والكشوفات", onBack) }
        item {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هامش الحساب", fontWeight = FontWeight.Bold)
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
        item {
            Text("عدد العناصر افتراضيًا", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("10", "20", "30", "50", "100").forEach { size ->
                    FilterChip(
                        selected = pageSize == size,
                        onClick = {
                            pageSize = size
                            vm.setSetting("page_size", size)
                        },
                        label = { Text(size) }
                    )
                }
            }
        }
        item {
            SettingSwitchRow("قفل العملية بعد المراجعة", reviewLock) {
                reviewLock = it
                vm.setSetting("review_lock", it.toString())
            }
        }
    }
}

@Composable
private fun CommissionSettings(vm: MainViewModel, onBack: () -> Unit) {
    val rules by vm.rules.collectAsState()
    var editing by remember { mutableStateOf<CommissionRule?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SettingsHeader("التذاكر والعمولات", onBack) }
        item {
            SettingsInfoCard(
                "قاعدة الحساب",
                "النسبة المئوية دائمًا على Base Fare لكل مسافر ثم تجمع. الرسم الثابت لكل مسافر. القواعد التاريخية لا تعيد حساب العمليات المراجعة."
            )
        }
        item {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("إضافة شركة / قاعدة")
            }
        }
        items(rules, key = { it.id }) { rule ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { editing = rule },
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rule.airline, fontWeight = FontWeight.Bold)
                        Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(ruleLabel(rule), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    rule.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    editing?.let { rule ->
        CommissionRuleDialog(vm, rule, onDismiss = { editing = null })
    }
    if (adding) {
        CommissionRuleDialog(
            vm,
            CommissionRule(
                id = "new",
                airline = "",
                kind = RuleKind.PRIVATE_MANUAL,
                value = 0.0
            ),
            onDismiss = { adding = false }
        )
    }
}

@Composable
private fun CommissionRuleDialog(vm: MainViewModel, initial: CommissionRule, onDismiss: () -> Unit) {
    var airline by remember(initial.id) { mutableStateOf(initial.airline) }
    var kind by remember(initial.id) { mutableStateOf(initial.kind) }
    var value by remember(initial.id) { mutableStateOf(if (initial.value == 0.0) "" else initial.value.toString()) }
    var roundTrip by remember(initial.id) { mutableStateOf(initial.roundTripValue?.toString().orEmpty()) }
    var effectiveFrom by remember(initial.id) { mutableStateOf(initial.effectiveFrom.orEmpty()) }
    var note by remember(initial.id) { mutableStateOf(initial.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == "new") "إضافة قاعدة عمولة" else "تعديل قاعدة العمولة") },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        airline, { airline = it }, Modifier.fillMaxWidth(),
                        label = { Text("شركة الطيران") }, singleLine = true
                    )
                }
                item {
                    Text("نوع القاعدة", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            RuleKind.PERCENT_BASE to "% Base Fare",
                            RuleKind.FIXED_PER_PASSENGER to "رسم لكل مسافر",
                            RuleKind.NONE to "بدون عمولة",
                            RuleKind.PRIVATE_MANUAL to "خاص/يدوي"
                        ).forEach { (k, label) ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) })
                        }
                    }
                }
                if (kind == RuleKind.PERCENT_BASE || kind == RuleKind.FIXED_PER_PASSENGER) {
                    item {
                        OutlinedTextField(
                            value, { value = it }, Modifier.fillMaxWidth(),
                            label = { Text(if (kind == RuleKind.PERCENT_BASE) "النسبة %" else "قيمة الرسم لكل مسافر") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                }
                if (kind == RuleKind.FIXED_PER_PASSENGER) {
                    item {
                        OutlinedTextField(
                            roundTrip, { roundTrip = it }, Modifier.fillMaxWidth(),
                            label = { Text("قيمة ذهاب وإياب - اختياري") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        effectiveFrom, { effectiveFrom = it }, Modifier.fillMaxWidth(),
                        label = { Text("سارية من YYYY-MM-DD - اختياري") },
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة القاعدة") }, minLines = 2)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = airline.isNotBlank(),
                onClick = {
                    vm.saveRule(
                        airline = airline,
                        kind = kind,
                        value = value.toDoubleOrNull() ?: 0.0,
                        roundTripValue = roundTrip.toDoubleOrNull(),
                        note = note.ifBlank { null },
                        effectiveFrom = effectiveFrom.ifBlank { null }
                    )
                    onDismiss()
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun WhatsAppSettings(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val defaultGroupUrl = "https://chat.whatsapp.com/CSubCIjAE5Y0qnzWOI5Z6K?s=cl&p=a&mlu=4&ilr=4"
    var contactType by remember { mutableStateOf(vm.setting("issuer_contact_type", "GROUP")) }
    var issuerPhone by remember { mutableStateOf(vm.setting("issuer_whatsapp", "")) }
    var issuerGroupUrl by remember { mutableStateOf(vm.setting("issuer_group_url", defaultGroupUrl)) }

    fun openTest() {
        if (contactType == "GROUP") {
            val url = issuerGroupUrl.trim()
            if (url.startsWith("https://chat.whatsapp.com/")) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } else {
            val phone = issuerPhone.filter(Char::isDigit)
            if (phone.isNotBlank()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")))
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader("واتساب", onBack) }
        item {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("جهة الإصدار", fontWeight = FontWeight.Bold)
                    Text(
                        "اختر طريقة فتح جهة الإصدار: رقم واتساب أو رابط كروب.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = contactType == "GROUP",
                            onClick = { contactType = "GROUP" },
                            label = { Text("رابط كروب") },
                            leadingIcon = { Icon(Icons.Rounded.Groups, null) }
                        )
                        FilterChip(
                            selected = contactType == "PHONE",
                            onClick = { contactType = "PHONE" },
                            label = { Text("رقم واتساب") },
                            leadingIcon = { Icon(Icons.Rounded.Phone, null) }
                        )
                    }

                    if (contactType == "GROUP") {
                        OutlinedTextField(
                            value = issuerGroupUrl,
                            onValueChange = { issuerGroupUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("رابط كروب WhatsApp") },
                            supportingText = { Text("مثال: https://chat.whatsapp.com/...") },
                            singleLine = false,
                            minLines = 2
                        )
                    } else {
                        OutlinedTextField(
                            value = issuerPhone,
                            onValueChange = { issuerPhone = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("رقم مع رمز الدولة") },
                            supportingText = { Text("أرقام فقط أو بصيغة +964...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                vm.setSetting("issuer_contact_type", contactType)
                                vm.setSetting("issuer_whatsapp", issuerPhone)
                                vm.setSetting("issuer_group_url", issuerGroupUrl.trim())
                            }
                        ) {
                            Icon(Icons.Rounded.Save, null)
                            Spacer(Modifier.width(4.dp))
                            Text("حفظ")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { openTest() }
                        ) {
                            Icon(Icons.Rounded.OpenInNew, null)
                            Spacer(Modifier.width(4.dp))
                            Text("اختبار")
                        }
                    }
                }
            }
        }
        item {
            SettingsInfoCard(
                "طريقة العمل",
                "زر «جهة الإصدار» داخل العملية يفتح الخيار المحفوظ مباشرة. إذا اخترت الكروب يفتح رابط المجموعة، وإذا اخترت الرقم يفتح المحادثة الفردية."
            )
        }
    }
}

@Composable
private fun InfoSettingsPage(title: String, items: List<String>, onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SettingsHeader(title, onBack) }
        items(items) { item ->
            SettingsInfoCard(item, "")
        }
    }
}

@Composable
private fun SettingsInfoCard(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
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
    val details = vm.txPassengerDetails(id)
    val context = LocalContext.current
    var edit by remember(id, tx.airline, tx.referenceTotal, tx.type, tx.note) { mutableStateOf(tx) }
    var airlinePicker by remember { mutableStateOf(false) }
    var rawOpen by remember { mutableStateOf(false) }
    val rule = rules.firstOrNull { it.airline.equals(edit.airline, true) }
    val tolerance = if (edit.currency == Currency.USD) {
        vm.setting("usd_tolerance", "1").toDoubleOrNull() ?: 1.0
    } else {
        vm.setting("iqd_tolerance", "1000").toDoubleOrNull() ?: 1000.0
    }
    val commission = CommissionEngine.calculate(
        passengers = details,
        actualSettlement = edit.amount,
        actualDiscount = edit.discount,
        referenceTotal = edit.referenceTotal,
        rule = rule,
        tolerance = tolerance,
        route = edit.route
    )
    val sourceGross = details.mapNotNull { it.amount }.sum()
    val sourceEquationDiff = if (sourceGross > 0.0 && edit.discount != 0.0) (sourceGross - edit.discount) - edit.amount else null
    val related = all.filter { it.id != id && !edit.pnr.isNullOrBlank() && it.pnr.equals(edit.pnr, true) }
    val next = all.firstOrNull { it.reviewState != ReviewState.REVIEWED && it.id != id }
    val customerPhone = vm.customerPhoneForTransaction(id)?.filter(Char::isDigit).orEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.95f), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(edit.pnr ?: labelFor(edit.type), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${labelFor(edit.type)} • ${edit.currency.name}${edit.operationNo?.let { " • #$it" } ?: ""}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        edit.externalId?.let { Text("ID: $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "إغلاق") }
                }

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (edit.type == TxType.UNKNOWN) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("تحديد نوع العملية", fontWeight = FontWeight.Bold)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            TxType.TICKET to "تذكرة",
                                            TxType.VISA to "فيزا",
                                            TxType.CHANGE to "تغيير",
                                            TxType.REFUND to "استرجاع",
                                            TxType.PAYMENT to "تسديد",
                                            TxType.VOID to "Void"
                                        ).forEach { (t, label) ->
                                            FilterChip(selected = edit.type == t, onClick = { edit = edit.copy(type = t) }, label = { Text(label) })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("بيانات العملية", fontWeight = FontWeight.Bold)
                                edit.transactionDate?.let { Text("التاريخ: $it") }
                                edit.route?.let { Text("المسار: $it") }
                                Text("التسديد في الكشف: ${formatMoney(edit.amount, edit.currency)}")
                                Text("Discount الكلي: ${formatMoney(edit.discount, edit.currency)}")
                                if (sourceGross > 0.0) Text("مجموع قيم المسافرين: ${formatMoney(sourceGross, edit.currency)}")
                                sourceEquationDiff?.let {
                                    Text(
                                        if (kotlin.math.abs(it) <= tolerance) "✓ مجموع التذاكر − Discount = التسديد" else "⚠ فرق معادلة المصدر: ${formatMoney(it, edit.currency)}",
                                        color = if (kotlin.math.abs(it) <= tolerance) Good else Warn
                                    )
                                }
                                edit.balanceAfter?.let { Text("الرصيد بعد العملية: ${formatMoney(it, edit.currency)}", fontSize = 12.sp) }
                            }
                        }
                    }

                    if (edit.type == TxType.TICKET || (edit.type == TxType.VOID && edit.pnr != null)) {
                        item {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { airlinePicker = true }
                            ) {
                                Icon(Icons.Rounded.Flight, null)
                                Spacer(Modifier.width(6.dp))
                                Text("شركة الطيران: " + (edit.airline ?: "مبهم — اختر الخط"))
                            }
                        }

                        if (rule?.kind == RuleKind.FIXED_PER_PASSENGER) {
                            item {
                                NullableNumberField(
                                    label = "سعر التذاكر قبل رسم الإصدار",
                                    value = edit.referenceTotal,
                                    modifier = Modifier.fillMaxWidth()
                                ) { edit = edit.copy(referenceTotal = it) }
                            }
                        }

                        item {
                            Text("المسافرون داخل PNR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Discount المصدر كلي؛ العمولة المتوقعة تحسب لكل مسافر من Base Fare ثم تجمع.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (details.isEmpty()) {
                            item { Text("لا توجد تفاصيل مسافرين في المصدر.", color = Warn) }
                        } else {
                            items(details, key = { it.passenger.id }) { d ->
                                PassengerAuditCard(vm, edit, d, rule)
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (commission.isWithinTolerance) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("نتيجة مراجعة العمولة", fontWeight = FontWeight.Bold)
                                    if (rule == null) {
                                        Text("الخط مبهم. اختر شركة الطيران لتظهر قاعدة العمولة.", color = Mystery)
                                    } else {
                                        Text(ruleLabel(rule))
                                        rule.note?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    commission.expectedCommission?.let {
                                        Text(
                                            if (rule?.kind == RuleKind.FIXED_PER_PASSENGER) "رسم الإصدار المتوقع: ${formatMoney(it, edit.currency)}"
                                            else "العمولة المتوقعة: ${formatMoney(it, edit.currency)}"
                                        )
                                    }
                                    commission.expectedSettlement?.let { Text("التسديد المتوقع: ${formatMoney(it, edit.currency)}") }
                                    commission.difference?.let { Text("الفرق: ${formatMoney(it, edit.currency)}") }
                                    Text(
                                        commission.explanation,
                                        color = when {
                                            commission.isWithinTolerance -> Good
                                            commission.needsInput -> Warn
                                            else -> Bad
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (edit.type == TxType.VISA || (edit.type == TxType.VOID && edit.visaCountry != null)) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("الفيزا: " + (edit.visaCountry ?: "غير محددة"), fontWeight = FontWeight.Bold)
                                    Text(if (edit.type == TxType.VOID) "الحالة: ملغاة" else "الحالة: بيع فيزا")
                                    details.forEach { d ->
                                        Text(
                                            "• ${d.passenger.name}" +
                                                (d.passenger.passport?.let { " • $it" } ?: "") +
                                                (d.amount?.let { " • ${formatMoney(it, edit.currency)}" } ?: "")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (edit.type == TxType.CHANGE) {
                        item { SettingsInfoCard("تغيير", "يسجل ويراجع فقط؛ لا يتم التحقق رياضيًا من رسم التغيير.") }
                    }
                    if (edit.type == TxType.REFUND) {
                        item { SettingsInfoCard("استرجاع", "يسجل ويراجع فقط؛ لا يتم فرض معادلة استرجاع تلقائية.") }
                    }

                    if (related.isNotEmpty()) {
                        item {
                            Text("عمليات أخرى بنفس PNR", fontWeight = FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                related.forEach { r ->
                                    AssistChip(
                                        onClick = { onNext(r.id) },
                                        label = { Text("${labelFor(r.type)} #${r.operationNo ?: "—"}") }
                                    )
                                }
                            }
                        }
                    }

                    edit.warning?.let { item { Text(it, color = Warn) } }

                    edit.rawText?.let { raw ->
                        item {
                            TextButton(onClick = { rawOpen = !rawOpen }) {
                                Icon(if (rawOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                                Spacer(Modifier.width(4.dp))
                                Text("بيانات المصدر ECX")
                            }
                            if (rawOpen) {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                                    Text(raw, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                                }
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
                            val type = vm.setting("issuer_contact_type", "GROUP")
                            if (type == "GROUP") {
                                val groupUrl = vm.setting(
                                    "issuer_group_url",
                                    "https://chat.whatsapp.com/CSubCIjAE5Y0qnzWOI5Z6K?s=cl&p=a&mlu=4&ilr=4"
                                ).trim()
                                if (groupUrl.startsWith("https://chat.whatsapp.com/")) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(groupUrl)))
                                }
                            } else {
                                val phone = vm.setting("issuer_whatsapp", "").filter(Char::isDigit)
                                if (phone.isNotBlank()) {
                                    val text = Uri.encode("PNR ${edit.pnr.orEmpty()} • عملية ${edit.operationNo.orEmpty()}")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$text")))
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Chat, null)
                        Spacer(Modifier.width(4.dp))
                        Text("جهة الإصدار")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = customerPhone.isNotBlank(),
                        onClick = {
                            val text = Uri.encode("PNR ${edit.pnr.orEmpty()}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$customerPhone?text=$text")))
                        }
                    ) {
                        Icon(Icons.Rounded.Person, null)
                        Spacer(Modifier.width(4.dp))
                        Text("الزبون")
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
                            if (edit.airline != tx.airline) vm.setAirline(edit.id, edit.airline)
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
                        onClick = {
                            vm.updateTransaction(edit)
                            if (edit.airline != tx.airline) vm.setAirline(edit.id, edit.airline)
                        }
                    ) { Text("حفظ") }
                }
            }
        }
    }

    if (airlinePicker) {
        AlertDialog(
            onDismissRequest = { airlinePicker = false },
            title = { Text("اختيار شركة الطيران") },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    item {
                        TextButton(onClick = {
                            edit = edit.copy(airline = null)
                            vm.setAirline(edit.id, null)
                            airlinePicker = false
                        }) { Text("مبهم / غير محدد") }
                    }
                    items(rules, key = { it.id }) { r ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                edit = edit.copy(airline = r.airline)
                                vm.setAirline(edit.id, r.airline)
                                airlinePicker = false
                            }
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(r.airline, fontWeight = FontWeight.Bold)
                                Text(ruleLabel(r), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun PassengerAuditCard(vm: MainViewModel, tx: Transaction, detail: TxPassengerDetail, rule: CommissionRule?) {
    var baseText by remember(detail.passenger.id, detail.baseFare) { mutableStateOf(detail.baseFare?.toString().orEmpty()) }
    val base = baseText.toDoubleOrNull()
    val taxes = if (detail.amount != null && base != null) detail.amount - base else null
    val expected = if (rule?.kind == RuleKind.PERCENT_BASE && base != null) base * rule.value / 100.0 else null

    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(detail.passenger.name, fontWeight = FontWeight.Bold)
                Text(detail.passengerType ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            detail.documentNo?.let { Text("Ticket: $it", fontSize = 12.sp) }
            detail.amount?.let { Text("Total المصدر: ${formatMoney(it, tx.currency)}") }
            OutlinedTextField(
                value = baseText,
                onValueChange = {
                    baseText = it
                    vm.setPassengerBaseFare(tx.id, detail.passenger.id, it.toDoubleOrNull())
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base Fare بدون ضرائب") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            taxes?.let { Text("Taxes المحسوبة: ${formatMoney(it, tx.currency)}") }
            expected?.let { Text("عمولة هذا المسافر: ${formatMoney(it, tx.currency)}", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun NullableNumberField(label: String, value: Double?, modifier: Modifier, onChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        text,
        {
            text = it
            onChange(it.toDoubleOrNull())
        },
        modifier,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
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
    tx.type == TxType.TICKET && tx.currency == Currency.USD && tx.airline.isNullOrBlank() -> "خط مبهم"
    tx.reviewState == ReviewState.REVIEWED -> "مراجع"
    tx.reviewState == ReviewState.FOLLOW_UP -> "متابعة"
    else -> "جديد"
}

private fun ruleLabel(rule: CommissionRule): String = when (rule.kind) {
    RuleKind.PERCENT_BASE -> "${rule.value}% من Base Fare"
    RuleKind.FIXED_PER_PASSENGER -> if (rule.roundTripValue != null)
        "+${rule.value} اتجاه واحد / +${rule.roundTripValue} ذهاب وإياب لكل مسافر"
    else "+${rule.value} لكل مسافر"
    RuleKind.PRIVATE_MANUAL -> "عمولة خاصة"
    RuleKind.NONE -> "بدون عمولة"
}

private fun formatMoney(value: Double, currency: Currency): String =
    if (currency == Currency.USD) "$" + String.format("%,.2f", value)
    else String.format("%,.0f د.ع", value)
