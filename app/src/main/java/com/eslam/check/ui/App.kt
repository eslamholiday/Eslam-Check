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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    val themeKey = preset.uppercase()
    val primary = remember(settingsRevision, themeKey) { vm.setting("theme_" + themeKey + "_primary", "") }
    val background = remember(settingsRevision, themeKey) { vm.setting("theme_" + themeKey + "_background", "") }
    val surface = remember(settingsRevision, themeKey) { vm.setting("theme_" + themeKey + "_surface", "") }
    val textColor = remember(settingsRevision, themeKey) { vm.setting("theme_" + themeKey + "_text", "") }
    val fontChoice = remember(settingsRevision, themeKey) { vm.setting("theme_" + themeKey + "_font", "SANS") }
    val fontScale = remember(settingsRevision, themeKey) { vm.setting("theme_" + themeKey + "_font_scale", "1.0").toFloatOrNull() ?: 1f }

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
        var detailQueue by remember { mutableStateOf<List<String>>(emptyList()) }
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
                    MainTab.HOME -> DashboardScreen(vm, { tab = MainTab.REVIEW }) {
                        detailQueue = emptyList()
                        detailId = it
                    }
                    MainTab.REVIEW -> ReviewScreen(vm) { id, queue ->
                        detailQueue = queue
                        detailId = id
                    }
                    MainTab.PASSENGERS -> PassengersScreen(vm) {
                        detailQueue = emptyList()
                        detailId = it
                    }
                    MainTab.PAYMENTS -> PaymentsScreen(vm) {
                        detailQueue = emptyList()
                        detailId = it
                    }
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
                TransactionDetailDialog(
                    vm = vm,
                    id = id,
                    navigationIds = detailQueue,
                    onDismiss = { detailId = null },
                    onNext = { next -> detailId = next }
                )
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
                        Text("ECX v3 / K1 • نص مختصر + Checksum + هوية ثابتة", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    placeholder = { Text("X3|K1|TY|DOC_ID|SNAPSHOT_ID|C\nL|I|...\nO|I|57336|2026-09-21|T|...\nQ|I|57336|NAME|A|TICKET|...\nH|...|CHECKSUM") }
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
                            Text("الإصدار: ECX v" + p.version + (if (p.dictionaryVersion.isNotBlank()) " / " + p.dictionaryVersion else ""), fontSize = 12.sp)
                            p.checksumVerified?.let { ok -> Text(if (ok) "Checksum ✓ سليم" else "Checksum ✗ غير مطابق", color = if (ok) Good else Bad, fontSize = 12.sp) }
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
private fun ReviewScreen(vm: MainViewModel, onDetail: (String, List<String>) -> Unit) {
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
    val queue = remember(filtered) { filtered.map { it.id } }

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

        Text(filtered.size.toString() + " عملية", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { tx ->
                TransactionCard(
                    vm = vm,
                    tx = tx,
                    showUnreviewAction = statusFilter == "REVIEWED",
                    onDetail = { onDetail(tx.id, queue) },
                    onReview = { vm.setReview(tx.id, ReviewState.REVIEWED) },
                    onUnreview = { vm.setReview(tx.id, ReviewState.UNREVIEWED) }
                )
            }
        }
    }
}

private data class CardAuditSummary(
    val text: String,
    val color: Color
)

@Composable
private fun TransactionCard(
    vm: MainViewModel,
    tx: Transaction,
    showUnreviewAction: Boolean = false,
    onDetail: () -> Unit,
    onReview: () -> Unit,
    onUnreview: () -> Unit = {}
) {
    val airlineMissing = tx.type == TxType.TICKET && tx.currency == Currency.USD && tx.airline.isNullOrBlank()
    val typeAccent = operationTypeColor(vm, tx.type)
    val accent = when {
        tx.changedAfterReview -> Warn
        tx.type == TxType.UNKNOWN || airlineMissing -> Mystery
        tx.reviewState == ReviewState.REVIEWED -> Good
        tx.reviewState == ReviewState.FOLLOW_UP -> Warn
        else -> typeAccent
    }
    val auditSummary = cardAuditSummary(vm, tx)

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDetail),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Row {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tx.pnr ?: labelFor(tx.type), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (tx.reviewState) {
                            ReviewState.REVIEWED -> {
                                Icon(Icons.Rounded.CheckCircle, "مراجع", tint = Good, modifier = Modifier.size(19.dp))
                                Text("مراجع", fontSize = 11.sp, color = Good)
                            }
                            ReviewState.FOLLOW_UP -> {
                                Icon(Icons.Rounded.Schedule, "متابعة", tint = Warn, modifier = Modifier.size(19.dp))
                                Text("متابعة", fontSize = 11.sp, color = Warn)
                            }
                            else -> {
                                Icon(Icons.Rounded.Cancel, "غير مراجع", tint = Bad, modifier = Modifier.size(19.dp))
                                Text("غير مراجع", fontSize = 11.sp, color = Bad)
                            }
                        }
                    }
                }
                Text(
                    labelFor(tx.type) + " • " + tx.currency.name + (tx.operationNo?.let { " • #" + it } ?: ""),
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
                    if (tx.type == TxType.TICKET && tx.discount != 0.0) {
                        Text("Discount " + formatMoney(tx.discount, tx.currency), fontSize = 12.sp)
                    }
                }

                auditSummary?.let { summary ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = summary.color.copy(alpha = 0.10f)
                    ) {
                        Text(
                            summary.text,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            color = summary.color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                tx.warning?.let { Text(it, color = Warn, fontSize = 12.sp) }

                if (showUnreviewAction && tx.reviewState == ReviewState.REVIEWED) {
                    OutlinedButton(
                        onClick = onUnreview,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Undo, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("إلغاء المراجعة")
                    }
                }
            }

            if (!showUnreviewAction && tx.reviewState != ReviewState.REVIEWED) {
                IconButton(onClick = onReview, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Icon(Icons.Rounded.CheckCircleOutline, "تمت المراجعة", tint = Good)
                }
            }
        }
    }
}

private fun cardAuditSummary(vm: MainViewModel, tx: Transaction): CardAuditSummary? {
    if (tx.changedAfterReview) return CardAuditSummary("⚠ تغيّرت بعد المراجعة", Warn)
    if (tx.type == TxType.UNKNOWN) return CardAuditSummary("؟ نوع العملية مبهم", Mystery)
    if (tx.type != TxType.TICKET) return null
    if (tx.currency == Currency.USD && tx.airline.isNullOrBlank()) {
        return CardAuditSummary("؟ خط غير محدد", Mystery)
    }

    val details = vm.txPassengerDetails(tx.id)
    if (details.isEmpty()) return CardAuditSummary("⚠ لا توجد بيانات مسافرين", Warn)

    val rule = vm.ruleForTransaction(tx)
    val numericRule = rule != null && rule.kind != RuleKind.PRIVATE_MANUAL
    val needsBase = when {
        rule?.kind == RuleKind.FIXED_PER_PASSENGER -> false
        rule?.kind == RuleKind.NONE -> false
        else -> details.any { it.baseFare == null }
    }
    if (needsBase) return CardAuditSummary("⚠ Base Fare ناقص", Warn)

    val tolerance = if (tx.currency == Currency.USD)
        vm.setting("usd_tolerance", "1").toDoubleOrNull() ?: 1.0
    else
        vm.setting("iqd_tolerance", "1000").toDoubleOrNull() ?: 1000.0

    val result = CommissionEngine.calculate(
        passengers = details,
        actualSettlement = tx.amount,
        actualDiscount = tx.discount,
        referenceTotal = tx.referenceTotal,
        rule = rule,
        tolerance = tolerance,
        route = tx.route
    )

    if (result.inferredFromDiscount && result.inferredRate != null) {
        return CardAuditSummary(
            "≈ عمولة مستنتجة " + String.format("%.2f", result.inferredRate) + "%",
            Navy
        )
    }
    if (!result.needsInput && !result.isWithinTolerance && result.difference != null) {
        return CardAuditSummary(
            "❌ فرق عمولة " + formatMoney(kotlin.math.abs(result.difference), tx.currency),
            Bad
        )
    }
    if (!result.needsInput && result.isWithinTolerance && numericRule) {
        return CardAuditSummary("✓ العمولة مطابقة", Good)
    }
    if (result.needsInput) return CardAuditSummary("⚠ تحتاج إكمال المراجعة", Warn)
    return null
}

@Composable
private fun PassengersScreen(vm: MainViewModel, onDetail: (String) -> Unit) {
    val passengers by vm.passengers.collectAsState()
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PassengerCategory.ALL) }
    var selected by remember { mutableStateOf<Passenger?>(null) }
    var mergeMode by remember { mutableStateOf(false) }
    var mergeSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMergeDialog by remember { mutableStateOf(false) }

    val filtered = remember(passengers, search, category) {
        val base = if (search.isBlank()) passengers else vm.passengerSuggestions(search)
        base.filter { p ->
            when (category) {
                PassengerCategory.ALL -> true
                PassengerCategory.RESPONSIBLE -> p.isResponsible
                PassengerCategory.DEPENDENT -> p.responsibleId != null
                PassengerCategory.INDEPENDENT -> !p.isResponsible && p.responsibleId == null
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("المسافرون", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (mergeMode) "حدد شخصين أو أكثر ثم اختر السجل الرئيسي."
                    else "الاسم الرئيسي يجمع الأسماء البديلة وكل العمليات القديمة والجديدة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            FilledTonalButton(
                onClick = {
                    mergeMode = !mergeMode
                    if (!mergeMode) mergeSelection = emptySet()
                }
            ) {
                Icon(if (mergeMode) Icons.Rounded.Close else Icons.Rounded.CallMerge, null)
                Spacer(Modifier.width(5.dp))
                Text(if (mergeMode) "إلغاء" else "دمج")
            }
        }

        OutlinedTextField(
            search, { search = it }, Modifier.fillMaxWidth(),
            label = { Text("بحث بالاسم / الاسم البديل / الهاتف / الجواز / ID") },
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

        if (mergeMode) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MergeType, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("تم تحديد " + mergeSelection.size + " مسافر", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { mergeSelection = emptySet() }) { Text("مسح") }
                }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { p ->
                val checked = p.id in mergeSelection
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (mergeMode) {
                            mergeSelection = if (checked) mergeSelection - p.id else mergeSelection + p.id
                        } else {
                            selected = p
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    border = if (checked) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (mergeMode) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value ->
                                    mergeSelection = if (value) mergeSelection + p.id else mergeSelection - p.id
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                        }
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
                        if (!mergeMode) Icon(Icons.Rounded.ChevronLeft, null)
                    }
                }
            }
        }

        if (mergeMode) {
            Button(
                enabled = mergeSelection.size >= 2,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showMergeDialog = true }
            ) {
                Icon(Icons.Rounded.CallMerge, null)
                Spacer(Modifier.width(6.dp))
                Text("دمج المحددين (" + mergeSelection.size + ")")
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

    if (showMergeDialog) {
        val candidates = passengers.filter { it.id in mergeSelection }
        MergePassengersDialog(
            passengers = candidates,
            onDismiss = { showMergeDialog = false },
            onConfirm = { primaryId ->
                vm.mergePassengers(primaryId, mergeSelection - primaryId)
                mergeSelection = emptySet()
                mergeMode = false
                showMergeDialog = false
            }
        )
    }
}

@Composable
private fun MergePassengersDialog(
    passengers: List<Passenger>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var primaryId by remember(passengers) { mutableStateOf(passengers.firstOrNull()?.id.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار السجل الرئيسي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "كل العمليات والأسماء السابقة ستظهر تحت السجل الرئيسي، وأي استيراد مستقبلي باسم قديم سيذهب إليه تلقائيًا.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        "الدمج قابل للفك لاحقًا من ملف الشخص.",
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp
                    )
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(passengers, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { primaryId = p.id }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = primaryId == p.id, onClick = { primaryId = p.id })
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                val details = listOfNotNull(
                                    p.phone?.takeIf { it.isNotBlank() },
                                    p.passport?.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (details.isNotBlank()) Text(details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = primaryId.isNotBlank(), onClick = { onConfirm(primaryId) }) {
                Text("دمج في هذا الملف")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
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
    var unmergeTarget by remember { mutableStateOf<Passenger?>(null) }
    var files by remember(passenger.id) { mutableStateOf(vm.passengerFiles(passenger.id)) }
    val aliases = vm.passengerAliases(passenger.id)
    val mergedRecords = vm.mergedPassengers(passenger.id)
    val dependents = vm.dependentsOf(passenger.id)
    val currentResponsible = edit.responsibleId?.let { id -> vm.passengerById(id) }
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
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Badge, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(7.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("هوية الشخص والأسماء البديلة", fontWeight = FontWeight.Bold)
                                        Text(
                                            "أي اسم مدمج سابقًا يبقى معروفًا ويذهب تلقائيًا إلى هذا الملف عند الاستيراد.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                val nameAliases = aliases
                                    .filter { it.kind == "NAME" }
                                    .map { it.value }
                                    .filter { !it.equals(edit.name, true) }
                                    .distinctBy { it.lowercase() }
                                if (nameAliases.isEmpty()) {
                                    Text("لا توجد أسماء بديلة بعد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        nameAliases.forEach { alias ->
                                            AssistChip(
                                                onClick = { copyToClipboard(context, "Alias", alias) },
                                                label = { Text(alias) },
                                                leadingIcon = { Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp)) }
                                            )
                                        }
                                    }
                                }

                                if (mergedRecords.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text("السجلات المدمجة (" + mergedRecords.size + ")", fontWeight = FontWeight.SemiBold)
                                    mergedRecords.forEach { source ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(source.name, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    listOfNotNull(source.passport, source.phone).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "سجل سابق" },
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TextButton(onClick = { unmergeTarget = source }) {
                                                Icon(Icons.Rounded.CallSplit, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text("فك الدمج")
                                            }
                                        }
                                    }
                                }
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

    unmergeTarget?.let { source ->
        AlertDialog(
            onDismissRequest = { unmergeTarget = null },
            title = { Text("فك دمج المسافر") },
            text = {
                Text(
                    "سيعود «" + source.name + "» كملف مستقل، وتبقى عملياته الأصلية محفوظة معه. لن يتم حذف أي عملية."
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.unmergePassenger(source.id)
                    unmergeTarget = null
                    onDismiss()
                }) { Text("فك الدمج") }
            },
            dismissButton = { TextButton(onClick = { unmergeTarget = null }) { Text("إلغاء") } }
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
        "visas" -> VisaSettings(vm) { page = "root" }
        "payments" -> PaymentsSettings(vm) { page = "root" }
        "appearance" -> AppearanceSettings(vm) { page = "root" }
        "people" -> InfoSettingsPage(
            title = "المسافرون والمسؤولون",
            items = listOf(
                "المسؤول يبقى مسافرًا بنفس ID",
                "اختيار مسؤول من داخل PNR يضم بقية المسافرين تحته",
                "يمكن دمج مسافرين واختيار سجل رئيسي واحد مع الاحتفاظ بالأسماء البديلة",
                "الاستيراد المستقبلي بأي اسم مدمج يعود تلقائيًا إلى السجل الرئيسي",
                "يمكن فك الدمج لاحقًا بدون حذف العمليات الأصلية",
                "ملفات الجواز ترفع كصور أو PDF بدل حقل جواز ظاهر",
                "الأسماء ملفات قابلة للفتح وتعرض العمليات الشخصية وعمليات التابعين"
            ),
            onBack = { page = "root" }
        )
        "data" -> InfoSettingsPage(
            title = "البيانات والنسخ",
            items = listOf(
                "البيانات محلية على الهاتف",
                "نفس العملية تحتفظ بنفس OP_ID عبر الكشوفات التراكمية",
                "بيانات المصدر منفصلة عن التعديلات اليدوية",
                "عمليات المراجعة تحفظ Snapshot لقاعدة العمولة المستخدمة"
            ),
            onBack = { page = "root" }
        )
        "advanced" -> InfoSettingsPage(
            title = "متقدم",
            items = listOf(
                "ECX v3 / K1 هو تنسيق النسخ المختصر الحالي مع دعم v1 وv2",
                "X3 يختصر الحساب والعملة والنوع والخطوط، والتطبيق يعرض الأسماء الكاملة",
                "Checksum يمنع استيراد نص ناقص أو متغير أثناء النسخ",
                "Change وNew Change = تغيير، Refund وNew Refund = استرجاع",
                "تذاكر IQD = Iraqi Airways تلقائيًا، وخط USD غير المعروف يبقى مبهمًا",
                "Visa وChange وRefund وPayment لا تحتاج Discount في الترجمة المختصرة"
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
        Triple("visas", "الفيز", Icons.Rounded.Description),
        Triple("people", "المسافرون والمسؤولون", Icons.Rounded.Groups),
        Triple("payments", "التسديدات والمحاسب", Icons.Rounded.Payments),
        Triple("whatsapp", "واتساب وجهة الإصدار", Icons.Rounded.Chat),
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
            Text("كل قاعدة قابلة للتعديل بدون تغيير بيانات المصدر.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(entries) { e ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(e.first) },
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
            SettingsInfoCard("لغة الاستيراد", "ECX v3 / K1 مختصر مع Checksum وهوية ثابتة • يدعم ECX v1 وv2 القديمين")
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
                "ECX v3 يشتق OP_ID ثابتًا من الحساب + العملة + رقم العملية. عند استيراد v1/v2 تبقى المطابقة القديمة مدعومة. Balance وترتيب الصفوف لا يغيّران هوية العملية."
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
    val airlines by vm.airlines.collectAsState()
    var editing by remember { mutableStateOf<CommissionRule?>(null) }
    var adding by remember { mutableStateOf(false) }
    var catalogOpen by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SettingsHeader("التذاكر والعمولات", onBack) }
        item {
            SettingsInfoCard(
                "نظام الخطوط",
                "شركة الطيران تختار من قاعدة بيانات الخطوط ولا تكتب يدويًا. يمكن لكل خط امتلاك عدة قواعد حسب التاريخ أو الاتجاه."
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("قاعدة عمولة")
                }
                OutlinedButton(onClick = { catalogOpen = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Flight, null)
                    Spacer(Modifier.width(5.dp))
                    Text("شركات الطيران")
                }
            }
        }
        items(rules.sortedWith(compareBy<CommissionRule> { it.airline }.thenByDescending { it.effectiveFrom.orEmpty() }), key = { it.id }) { rule ->
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
                    if (!rule.effectiveFrom.isNullOrBlank()) Text("سارية من: " + rule.effectiveFrom, fontSize = 12.sp)
                    if (rule.direction != "ANY") Text("الاتجاه: " + if (rule.direction == "REVERSE") "عكسي" else "طبيعي", fontSize = 12.sp)
                    rule.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    editing?.let { rule ->
        CommissionRuleDialog(vm, rule, airlines, onDismiss = { editing = null })
    }
    if (adding) {
        CommissionRuleDialog(
            vm = vm,
            initial = CommissionRule(id = "new", airline = airlines.firstOrNull()?.name.orEmpty(), kind = RuleKind.PRIVATE_MANUAL, value = 0.0),
            airlines = airlines,
            onDismiss = { adding = false }
        )
    }
    if (catalogOpen) AirlineCatalogDialog(vm, airlines) { catalogOpen = false }
}

@Composable
private fun CommissionRuleDialog(
    vm: MainViewModel,
    initial: CommissionRule,
    airlines: List<AirlineInfo>,
    onDismiss: () -> Unit
) {
    var airline by remember(initial.id) { mutableStateOf(initial.airline) }
    var airlinePicker by remember { mutableStateOf(false) }
    var kind by remember(initial.id) { mutableStateOf(initial.kind) }
    var value by remember(initial.id) { mutableStateOf(if (initial.value == 0.0) "" else initial.value.toString()) }
    var roundTrip by remember(initial.id) { mutableStateOf(initial.roundTripValue?.toString().orEmpty()) }
    var effectiveFrom by remember(initial.id) { mutableStateOf(initial.effectiveFrom.orEmpty()) }
    var direction by remember(initial.id) { mutableStateOf(initial.direction) }
    var note by remember(initial.id) { mutableStateOf(initial.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == "new") "إضافة قاعدة عمولة" else "تعديل قاعدة العمولة") },
        text = {
            LazyColumn(Modifier.heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(onClick = { airlinePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Flight, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (airline.isBlank()) "اختر شركة الطيران" else airline)
                    }
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
                            label = { Text(if (kind == RuleKind.PERCENT_BASE) "النسبة %" else "القيمة لكل مسافر") },
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
                    Text("الاتجاه", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ANY" to "الكل", "NORMAL" to "طبيعي", "REVERSE" to "عكسي").forEach { (key, label) ->
                            FilterChip(selected = direction == key, onClick = { direction = key }, label = { Text(label) })
                        }
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
                        effectiveFrom = effectiveFrom.ifBlank { null },
                        direction = direction,
                        ruleId = initial.id.takeUnless { it == "new" }
                    )
                    onDismiss()
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (airlinePicker) {
        AirlineSelectionDialog(
            airlines = airlines,
            selected = airline,
            allowUnknown = false,
            onDismiss = { airlinePicker = false },
            onSelect = {
                airline = it?.name.orEmpty()
                airlinePicker = false
            }
        )
    }
}

@Composable
private fun AirlineCatalogDialog(vm: MainViewModel, airlines: List<AirlineInfo>, onDismiss: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("شركات الطيران") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Button(onClick = { adding = !adding }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(5.dp))
                        Text("إضافة شركة طيران")
                    }
                }
                if (adding) {
                    item {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم الشركة") })
                    }
                    item {
                        OutlinedTextField(code, { code = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("كود مختصر - اختياري") })
                    }
                    item {
                        Button(
                            enabled = name.isNotBlank(),
                            onClick = {
                                vm.addAirline(code, name)
                                name = ""
                                code = ""
                                adding = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("حفظ الشركة") }
                    }
                }
                items(airlines, key = { it.id }) { airline ->
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(airline.code, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(airline.name, Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}

@Composable
private fun AirlineSelectionDialog(
    airlines: List<AirlineInfo>,
    selected: String?,
    allowUnknown: Boolean = true,
    onDismiss: () -> Unit,
    onSelect: (AirlineInfo?) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val visible = airlines.filter { search.isBlank() || it.name.contains(search, true) || it.code.contains(search, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار شركة الطيران") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    search, { search = it }, Modifier.fillMaxWidth(),
                    label = { Text("بحث") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (allowUnknown) {
                        item {
                            TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                                Text("مبهم / غير محدد")
                            }
                        }
                    }
                    items(visible, key = { it.id }) { a ->
                        TextButton(onClick = { onSelect(a) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(a.name, fontWeight = if (a.name.equals(selected, true)) FontWeight.Bold else FontWeight.Normal)
                                Text(a.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
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
private fun VisaSettings(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var link by remember { mutableStateOf(vm.setting("visa_link", "https://docs.google.com/spreadsheets/d/1NwV7H_9AGEWunvE6rY5d-U4qi6lOkME23oawFA2jx_Q/edit?usp=drivesdk")) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader("الفيز", onBack) }
        item {
            SettingsInfoCard(
                "مراجعة الفيز",
                "لا يظهر Discount في الفيز. يعرض النوع/الدولة والأسماء والأسعار فقط، والإلغاء يبقى واضحًا."
            )
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("رابط الفيز", fontWeight = FontWeight.Bold)
                    OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), label = { Text("الرابط الافتراضي") }, minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.setSetting("visa_link", link.trim()) },
                            modifier = Modifier.weight(1f)
                        ) { Text("حفظ") }
                        OutlinedButton(
                            enabled = link.startsWith("http"),
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.trim()))) },
                            modifier = Modifier.weight(1f)
                        ) { Text("فتح") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsSettings(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(vm.setting("accountant_name", "المحاسب")) }
    var phone by remember { mutableStateOf(vm.setting("accountant_whatsapp", "")) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader("التسديدات والمحاسب", onBack) }
        item {
            SettingsInfoCard(
                "التسديدات",
                "لا يظهر Discount. يمكن حفظ رابط/مرفق لكل عملية، وزر المحاسب يفتح واتساب الرقم المحفوظ."
            )
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم المحاسب") })
                    OutlinedTextField(
                        phone, { phone = it }, Modifier.fillMaxWidth(),
                        label = { Text("رقم واتساب المحاسب") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                vm.setSetting("accountant_name", name.ifBlank { "المحاسب" })
                                vm.setSetting("accountant_whatsapp", phone)
                            }
                        ) { Text("حفظ") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = phone.filter(Char::isDigit).isNotBlank(),
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone.filter(Char::isDigit))))
                            }
                        ) { Text("اختبار WhatsApp") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettings(vm: MainViewModel, onBack: () -> Unit) {
    val revision by vm.settingsRevision.collectAsState()
    var preset by remember(revision) { mutableStateOf(vm.setting("theme_preset", "NAVY")) }
    val themeKey = preset.uppercase()
    var fontChoice by remember(revision, themeKey) { mutableStateOf(vm.setting("theme_" + themeKey + "_font", "SANS")) }
    var fontScale by remember(revision, themeKey) { mutableStateOf(vm.setting("theme_" + themeKey + "_font_scale", "1.0").toFloatOrNull() ?: 1f) }
    var colorTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val themeRows = listOf(
        Triple("theme_" + themeKey + "_primary", "اللون الأساسي", ""),
        Triple("theme_" + themeKey + "_background", "لون الخلفية", ""),
        Triple("theme_" + themeKey + "_surface", "لون البطاقات", ""),
        Triple("theme_" + themeKey + "_text", "لون الخط", "")
    )

    val typeRows = listOf(
        Triple("color_ticket", "تذكرة", defaultTypeColor("color_ticket")),
        Triple("color_visa", "فيزا", defaultTypeColor("color_visa")),
        Triple("color_change", "تغيير", defaultTypeColor("color_change")),
        Triple("color_refund", "استرجاع", defaultTypeColor("color_refund")),
        Triple("color_void", "إلغاء / Void", defaultTypeColor("color_void")),
        Triple("color_payment", "تسديد", defaultTypeColor("color_payment")),
        Triple("color_unknown", "مبهم", defaultTypeColor("color_unknown"))
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader("الشكل والواجهة", onBack) }
        item {
            Text("5 ثيمات قابلة للتعديل", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "NAVY" to "Eslam Navy",
                    "MIDNIGHT" to "Midnight",
                    "EMERALD" to "Emerald",
                    "GRAPHITE" to "Graphite",
                    "SAND" to "Light Sand"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = preset == key,
                        onClick = {
                            preset = key
                            vm.setSetting("theme_preset", key)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("خط هذا الثيم", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SANS" to "Sans", "SERIF" to "Serif", "MONO" to "Mono").forEach { (key, label) ->
                            FilterChip(
                                selected = fontChoice == key,
                                onClick = {
                                    fontChoice = key
                                    vm.setSetting("theme_" + themeKey + "_font", key)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text("حجم الخط: " + String.format("%.0f%%", fontScale * 100), fontSize = 12.sp)
                    Slider(
                        value = fontScale,
                        onValueChange = {
                            fontScale = it
                            vm.setSetting("theme_" + themeKey + "_font_scale", it.toString())
                        },
                        valueRange = 0.85f..1.30f
                    )
                }
            }
        }

        item { Text("ألوان هذا الثيم", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        items(themeRows) { row ->
            val value = vm.setting(row.first, "")
            val preview = when {
                value.isNotBlank() -> colorFromHex(value)
                row.first.endsWith("_primary") -> MaterialTheme.colorScheme.primary
                row.first.endsWith("_background") -> MaterialTheme.colorScheme.background
                row.first.endsWith("_surface") -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.onSurface
            } ?: MaterialTheme.colorScheme.primary
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { colorTarget = row },
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(preview, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(row.second, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(if (value.isBlank()) "افتراضي" else value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Palette, null)
                }
            }
        }

        item { Text("ألوان أنواع العمليات", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        items(typeRows) { row ->
            val value = vm.setting(row.first, row.third)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { colorTarget = row },
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(colorFromHex(value) ?: MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(row.second, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Palette, null)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        themeRows.forEach { vm.setSetting(it.first, "") }
                        vm.setSetting("theme_" + themeKey + "_font", "SANS")
                        vm.setSetting("theme_" + themeKey + "_font_scale", "1.0")
                    }
                ) { Text("إعادة الثيم") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { typeRows.forEach { vm.setSetting(it.first, it.third) } }
                ) { Text("ألوان العمليات") }
            }
        }
    }

    colorTarget?.let { target ->
        val current = vm.setting(target.first, target.third)
        ColorChoiceDialog(
            title = "لون " + target.second,
            current = current,
            allowDefault = target.third.isBlank(),
            onDismiss = { colorTarget = null },
            onSelect = {
                vm.setSetting(target.first, it)
                colorTarget = null
            }
        )
    }
}

@Composable
private fun ColorChoiceDialog(
    title: String,
    current: String,
    allowDefault: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val palette = listOf(
        "#2F80ED", "#56CCF2", "#7B61FF", "#9B51E0", "#27AE60", "#6FCF97",
        "#F2994A", "#F2C94C", "#EB5757", "#D4A84F", "#607D8B", "#90A4AE",
        "#101820", "#17212B", "#F5F7FA", "#FFF8E7"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowDefault) {
                    OutlinedButton(onClick = { onSelect("") }, modifier = Modifier.fillMaxWidth()) {
                        Text("استخدام لون الثيم الافتراضي")
                    }
                }
                palette.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { hex ->
                            Surface(
                                modifier = Modifier.size(52.dp).clickable { onSelect(hex) },
                                shape = CircleShape,
                                color = colorFromHex(hex) ?: MaterialTheme.colorScheme.primary,
                                border = if (hex.equals(current, true)) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null
                            ) {}
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun defaultTypeColor(key: String): String = when (key) {
    "color_ticket" -> "#2F80ED"
    "color_visa" -> "#7B61FF"
    "color_change" -> "#F2994A"
    "color_refund" -> "#27AE60"
    "color_void" -> "#EB5757"
    "color_payment" -> "#56CCF2"
    else -> "#6A4C93"
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
private fun TransactionDetailDialog(
    vm: MainViewModel,
    id: String,
    navigationIds: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onNext: (String) -> Unit
) {
    val all by vm.transactions.collectAsState()
    val rules by vm.rules.collectAsState()
    val airlines by vm.airlines.collectAsState()
    val allPassengers by vm.passengers.collectAsState()
    val tx = all.firstOrNull { it.id == id } ?: vm.transaction(id) ?: return
    val details = vm.txPassengerDetails(id)
    val context = LocalContext.current
    var edit by remember(id, tx.airline, tx.referenceTotal, tx.type, tx.note, tx.externalLink, tx.reviewState) { mutableStateOf(tx) }
    var airlinePicker by remember { mutableStateOf(false) }
    var commissionEditor by remember { mutableStateOf(false) }
    var responsiblePicker by remember { mutableStateOf(false) }
    var selectedPassenger by remember { mutableStateOf<Passenger?>(null) }
    var rawOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }

    val rule = vm.ruleForTransaction(edit)
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
    val sourceEquationDiff = if (edit.type == TxType.TICKET && sourceGross > 0.0) (sourceGross - edit.discount) - edit.amount else null
    val related = vm.relatedOperations(id)
    val queueIndex = navigationIds.indexOf(id)
    val smartNextId = if (queueIndex >= 0) navigationIds.getOrNull(queueIndex + 1) else null
    val allIndex = all.indexOfFirst { it.id == id }
    val plainNextId = smartNextId ?: if (navigationIds.isEmpty() && allIndex >= 0) all.getOrNull(allIndex + 1)?.id else null
    val reviewNextId = smartNextId ?: if (navigationIds.isEmpty() && allIndex >= 0) {
        all.drop(allIndex + 1).firstOrNull { it.reviewState != ReviewState.REVIEWED }?.id
    } else null
    val responsible = vm.responsibleForTransaction(id)
    val pnrPassengers = remember(id, related, allPassengers) {
        val ids = linkedSetOf<String>()
        val out = mutableListOf<Passenger>()
        (listOf(tx) + related).forEach { item ->
            if (edit.pnr.isNullOrBlank() || item.pnr.equals(edit.pnr, true)) {
                vm.passengersFor(item.id).forEach { p ->
                    if (ids.add(p.id)) out += p
                }
            }
        }
        if (out.isEmpty()) details.map { it.passenger } else out
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.96f), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                edit.pnr ?: labelFor(edit.type),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = if (!edit.pnr.isNullOrBlank()) Modifier.clickable {
                                    copyToClipboard(context, "PNR", edit.pnr.orEmpty())
                                } else Modifier
                            )
                            if (!edit.pnr.isNullOrBlank()) {
                                IconButton(onClick = { copyToClipboard(context, "PNR", edit.pnr.orEmpty()) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.ContentCopy, "نسخ PNR", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                labelFor(edit.type) + " • " + edit.currency.name,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            edit.operationNo?.let { op ->
                                Text(
                                    "#" + op,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { copyToClipboard(context, "رقم العملية", op) }
                                )
                                IconButton(
                                    onClick = { copyToClipboard(context, "رقم العملية", op) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, "نسخ رقم العملية", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        edit.externalId?.let { Text("ID: " + it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                                            TxType.VOID to "إلغاء"
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
                                edit.transactionDate?.let { Text("التاريخ: " + it) }
                                edit.route?.let { route ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("المسار: " + route, Modifier.weight(1f))
                                        IconButton(onClick = { copyToClipboard(context, "Route", route) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Rounded.ContentCopy, "نسخ المسار", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                Text("التسديد في الكشف: " + formatMoney(edit.amount, edit.currency))
                                if (edit.type == TxType.TICKET) {
                                    Text("Discount الكلي: " + formatMoney(edit.discount, edit.currency))
                                    if (sourceGross > 0.0) Text("مجموع قيم المسافرين: " + formatMoney(sourceGross, edit.currency))
                                    sourceEquationDiff?.let { diff ->
                                        Text(
                                            if (kotlin.math.abs(diff) <= tolerance) "✓ مجموع التذاكر − Discount = التسديد"
                                            else "⚠ فرق معادلة المصدر: " + formatMoney(diff, edit.currency),
                                            color = if (kotlin.math.abs(diff) <= tolerance) Good else Warn
                                        )
                                    }
                                }
                                edit.balanceAfter?.let { Text("الرصيد بعد العملية: " + formatMoney(it, edit.currency), fontSize = 12.sp) }
                            }
                        }
                    }

                    if (edit.type == TxType.TICKET || (edit.type == TxType.VOID && edit.pnr != null && edit.visaCountry == null)) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { airlinePicker = true }
                                ) {
                                    Icon(Icons.Rounded.Flight, null)
                                    Spacer(Modifier.width(5.dp))
                                    Text(edit.airline ?: "الخط مبهم", maxLines = 2)
                                }
                                FilledTonalButton(
                                    modifier = Modifier.weight(1f),
                                    enabled = !edit.airline.isNullOrBlank(),
                                    onClick = { commissionEditor = true }
                                ) {
                                    Icon(Icons.Rounded.Percent, null)
                                    Spacer(Modifier.width(5.dp))
                                    Text(rule?.let(::ruleLabel) ?: "تحديد العمولة", maxLines = 2)
                                }
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("المسافرون داخل PNR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Base Fare لكل مسافر → Taxes → العمولة المتوقعة → المقارنة مع Discount.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (details.size > 1 && responsible == null) {
                                    TextButton(onClick = { responsiblePicker = true }) {
                                        Icon(Icons.Rounded.SupervisorAccount, null)
                                        Spacer(Modifier.width(3.dp))
                                        Text("تحديد مسؤول")
                                    }
                                }
                            }
                        }

                        if (details.isEmpty()) {
                            item { Text("لا توجد تفاصيل مسافرين في المصدر.", color = Warn) }
                        } else {
                            items(details, key = { it.passenger.id }) { d ->
                                PassengerAuditCard(
                                    vm = vm,
                                    tx = edit,
                                    detail = d,
                                    rule = rule,
                                    onOpenPassenger = { selectedPassenger = it }
                                )
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
                                        Text("اختر شركة الطيران ثم حدد قاعدة العمولة.", color = Mystery)
                                    } else {
                                        Text(ruleLabel(rule))
                                        if (!rule.effectiveFrom.isNullOrBlank()) Text("سارية من " + rule.effectiveFrom, fontSize = 12.sp)
                                        rule.note?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    commission.expectedCommission?.let {
                                        Text(
                                            if (rule?.kind == RuleKind.FIXED_PER_PASSENGER) "رسم الإصدار المتوقع: " + formatMoney(it, edit.currency)
                                            else "العمولة المتوقعة: " + formatMoney(it, edit.currency)
                                        )
                                    }
                                    commission.expectedSettlement?.let { Text("التسديد المتوقع: " + formatMoney(it, edit.currency)) }
                                    commission.difference?.let { Text("الفرق: " + formatMoney(it, edit.currency)) }
                                    Text(
                                        commission.explanation,
                                        color = when {
                                            commission.isWithinTolerance -> Good
                                            commission.needsInput -> Warn
                                            else -> Bad
                                        }
                                    )
                                    edit.commissionRuleSnapshot?.let {
                                        HorizontalDivider()
                                        Text("Snapshot المراجعة محفوظ", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    if (edit.type == TxType.VISA || (edit.type == TxType.VOID && edit.visaCountry != null)) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("الفيزا: " + (edit.visaCountry ?: "غير محددة"), fontWeight = FontWeight.Bold)
                                    Text(if (edit.type == TxType.VOID) "الحالة: ملغاة" else "الحالة: بيع فيزا")
                                    details.forEach { d ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                d.passenger.name,
                                                Modifier.weight(1f).clickable { selectedPassenger = d.passenger },
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            d.amount?.let { Text(formatMoney(it, edit.currency)) }
                                            IconButton(onClick = { copyToClipboard(context, "الاسم", d.passenger.name) }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Rounded.ContentCopy, "نسخ الاسم", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    if (details.size > 1 && responsible == null) {
                                        OutlinedButton(onClick = { responsiblePicker = true }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Rounded.SupervisorAccount, null)
                                            Spacer(Modifier.width(5.dp))
                                            Text("تعيين مسؤول لهذه المعاملة")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (edit.type == TxType.CHANGE) {
                        item { SettingsInfoCard("تغيير", "يسجل ويراجع فقط؛ لا يظهر Discount ولا يتم فرض معادلة رسم التغيير.") }
                    }
                    if (edit.type == TxType.REFUND) {
                        item { SettingsInfoCard("استرجاع", "يسجل ويراجع فقط؛ لا يظهر Discount ولا يتم فرض معادلة استرجاع تلقائية.") }
                    }
                    if (edit.type == TxType.PAYMENT) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("رابط التسديد / المرفق", fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        edit.externalLink.orEmpty(),
                                        { edit = edit.copy(externalLink = it.ifBlank { null }) },
                                        Modifier.fillMaxWidth(),
                                        label = { Text("ألصق رابط الإيصال أو المستند") },
                                        minLines = 2
                                    )
                                    if (edit.externalLink.orEmpty().startsWith("http")) {
                                        OutlinedButton(
                                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(edit.externalLink))) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.OpenInNew, null)
                                            Spacer(Modifier.width(5.dp))
                                            Text("فتح الرابط")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (related.isNotEmpty()) {
                        item {
                            Text("العمليات المرتبطة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("التذكرة الأصلية والتغييرات والاسترجاعات والإلغاء المرتبط بنفس PNR/رقم التذكرة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                related.forEach { r ->
                                    AssistChip(
                                        onClick = { onNext(r.id) },
                                        label = { Text(labelFor(r.type) + " #" + (r.operationNo ?: "—")) },
                                        leadingIcon = {
                                            Icon(
                                                when (r.type) {
                                                    TxType.TICKET -> Icons.Rounded.ConfirmationNumber
                                                    TxType.CHANGE -> Icons.Rounded.SwapHoriz
                                                    TxType.REFUND -> Icons.Rounded.Replay
                                                    TxType.VOID -> Icons.Rounded.Cancel
                                                    else -> Icons.Rounded.Link
                                                },
                                                null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    edit.warning?.let { item { Text(it, color = Warn) } }

                    item {
                        TextButton(onClick = { historyOpen = !historyOpen }) {
                            Icon(if (historyOpen) Icons.Rounded.ExpandLess else Icons.Rounded.History, null)
                            Spacer(Modifier.width(4.dp))
                            Text("السجل والتغييرات")
                        }
                        if (historyOpen) {
                            val events = vm.auditEvents("transaction", edit.id)
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (events.isEmpty()) {
                                        Text("لا يوجد سجل بعد", fontSize = 12.sp)
                                    } else {
                                        events.take(20).forEach { event ->
                                            Text(
                                                auditActionLabel(event.action) + (event.details?.takeIf { it.isNotBlank() }?.let { " • " + it } ?: ""),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(formatAuditTime(event.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }

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
                            when {
                                edit.type == TxType.VISA || (edit.type == TxType.VOID && edit.visaCountry != null) -> {
                                    val link = vm.setting("visa_link", "").trim()
                                    if (link.startsWith("http")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                }
                                edit.type == TxType.PAYMENT -> {
                                    val phone = vm.setting("accountant_whatsapp", "").filter(Char::isDigit)
                                    if (phone.isNotBlank()) {
                                        val msg = Uri.encode(
                                            "تسديد #" + edit.operationNo.orEmpty() + " • " +
                                                formatMoney(edit.amount, edit.currency) + " • " + edit.transactionDate.orEmpty()
                                        )
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone + "?text=" + msg)))
                                    }
                                }
                                else -> openIssuerContact(context, vm, edit)
                            }
                        }
                    ) {
                        Icon(
                            when {
                                edit.type == TxType.VISA || (edit.type == TxType.VOID && edit.visaCountry != null) -> Icons.Rounded.Description
                                edit.type == TxType.PAYMENT -> Icons.Rounded.AccountCircle
                                else -> Icons.Rounded.Chat
                            },
                            null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                edit.type == TxType.VISA || (edit.type == TxType.VOID && edit.visaCountry != null) -> "رابط الفيز"
                                edit.type == TxType.PAYMENT -> vm.setting("accountant_name", "المحاسب")
                                else -> "جهة الإصدار"
                            }
                        )
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = details.isNotEmpty() || responsible != null,
                        onClick = {
                            val current = responsible
                            if (current == null) {
                                responsiblePicker = true
                            } else {
                                val phone = current.phone.orEmpty().filter(Char::isDigit)
                                if (phone.isNotBlank()) {
                                    val msg = Uri.encode("PNR " + edit.pnr.orEmpty())
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone + "?text=" + msg)))
                                } else {
                                    selectedPassenger = current
                                }
                            }
                        }
                    ) {
                        Icon(if (responsible?.phone.orEmpty().filter(Char::isDigit).isNotBlank()) Icons.Rounded.Chat else Icons.Rounded.SupervisorAccount, null)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                responsible == null -> "تحديد المسؤول"
                                responsible.phone.orEmpty().filter(Char::isDigit).isNotBlank() -> responsible.name + " • واتساب"
                                else -> "المسؤول: " + responsible.name
                            },
                            maxLines = 2
                        )
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
        AirlineSelectionDialog(
            airlines = airlines,
            selected = edit.airline,
            allowUnknown = true,
            onDismiss = { airlinePicker = false },
            onSelect = { chosen ->
                edit = edit.copy(airline = chosen?.name)
                vm.setAirline(edit.id, chosen?.name)
                airlinePicker = false
            }
        )
    }

    if (commissionEditor && !edit.airline.isNullOrBlank()) {
        CommissionRuleDialog(
            vm = vm,
            initial = rule ?: CommissionRule(
                id = "new",
                airline = edit.airline.orEmpty(),
                kind = RuleKind.PRIVATE_MANUAL,
                value = 0.0
            ),
            airlines = airlines,
            onDismiss = { commissionEditor = false }
        )
    }

    if (responsiblePicker) {
        AlertDialog(
            onDismissRequest = { responsiblePicker = false },
            title = { Text("تحديد المسؤول") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (!edit.pnr.isNullOrBlank()) "اختيار أحد مسافري PNR سيضم بقية المسافرين تحته تلقائيًا."
                        else "اختيار مسؤول سيضم بقية الأشخاص في هذه العملية تحته.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(pnrPassengers, key = { it.id }) { p ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (!edit.pnr.isNullOrBlank()) vm.assignResponsibleForPnr(edit.pnr.orEmpty(), p.id)
                                    else vm.assignResponsibleForTransaction(edit.id, p.id)
                                    responsiblePicker = false
                                }
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Person, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(p.name, Modifier.weight(1f))
                                    if (!p.phone.isNullOrBlank()) Icon(Icons.Rounded.Chat, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    selectedPassenger?.let { p ->
        key("profile-" + p.id) {
            PassengerDetailDialog(
                vm = vm,
                passenger = p,
                allPassengers = allPassengers,
                onDismiss = { selectedPassenger = null },
                onOpenTransaction = {
                    selectedPassenger = null
                    onNext(it)
                },
                onOpenPassenger = { selectedPassenger = it }
            )
        }
    }
}

@Composable
private fun PassengerAuditCard(
    vm: MainViewModel,
    tx: Transaction,
    detail: TxPassengerDetail,
    rule: CommissionRule?,
    onOpenPassenger: (Passenger) -> Unit
) {
    val context = LocalContext.current
    var baseText by remember(detail.passenger.id, detail.baseFare) { mutableStateOf(detail.baseFare?.toString().orEmpty()) }
    val base = baseText.toDoubleOrNull()
    val taxes = if (detail.amount != null && base != null) detail.amount - base else null
    val expected = if (rule?.kind == RuleKind.PERCENT_BASE && base != null) base * rule.value / 100.0 else null

    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    detail.passenger.name,
                    modifier = Modifier.weight(1f).clickable { onOpenPassenger(detail.passenger) },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { copyToClipboard(context, "الاسم", detail.passenger.name) },
                    modifier = Modifier.size(30.dp)
                ) { Icon(Icons.Rounded.ContentCopy, "نسخ الاسم", modifier = Modifier.size(16.dp)) }
                Text(detail.passengerType ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            detail.documentNo?.let { ticket ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ticket: " + ticket,
                        modifier = Modifier.weight(1f).clickable { copyToClipboard(context, "Ticket", ticket) },
                        fontSize = 12.sp
                    )
                    IconButton(
                        onClick = { copyToClipboard(context, "Ticket", ticket) },
                        modifier = Modifier.size(30.dp)
                    ) { Icon(Icons.Rounded.ContentCopy, "نسخ رقم التذكرة", modifier = Modifier.size(16.dp)) }
                }
            }
            detail.amount?.let { Text("Total المصدر: " + formatMoney(it, tx.currency)) }
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
            taxes?.let { Text("Taxes المحسوبة: " + formatMoney(it, tx.currency)) }
            expected?.let { Text("عمولة هذا المسافر: " + formatMoney(it, tx.currency), color = MaterialTheme.colorScheme.primary) }
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

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
}

private fun openIssuerContact(context: Context, vm: MainViewModel, tx: Transaction) {
    val type = vm.setting("issuer_contact_type", "GROUP")
    if (type == "GROUP") {
        val url = vm.setting(
            "issuer_group_url",
            "https://chat.whatsapp.com/CSubCIjAE5Y0qnzWOI5Z6K?s=cl&p=a&mlu=4&ilr=4"
        ).trim()
        if (url.startsWith("http")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } else {
        val phone = vm.setting("issuer_whatsapp", "").filter(Char::isDigit)
        if (phone.isNotBlank()) {
            val message = Uri.encode("PNR " + tx.pnr.orEmpty() + " • عملية " + tx.operationNo.orEmpty())
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone + "?text=" + message)))
        }
    }
}

@Composable
private fun operationTypeColor(vm: MainViewModel, type: TxType): Color {
    val revision by vm.settingsRevision.collectAsState()
    val key = when (type) {
        TxType.TICKET -> "color_ticket"
        TxType.VISA -> "color_visa"
        TxType.CHANGE -> "color_change"
        TxType.REFUND -> "color_refund"
        TxType.PAYMENT -> "color_payment"
        TxType.VOID -> "color_void"
        else -> "color_unknown"
    }
    val hex = remember(revision, key) { vm.setting(key, defaultTypeColor(key)) }
    return colorFromHex(hex) ?: MaterialTheme.colorScheme.primary
}

private fun auditActionLabel(action: String): String = when (action) {
    "source_snapshot" -> "استيراد/تحديث من المصدر"
    "review" -> "تغيير حالة المراجعة"
    "edit" -> "تعديل يدوي"
    "airline" -> "تحديد شركة الطيران"
    "base_fare" -> "تعديل Base Fare"
    "assign_responsible_group" -> "تعيين مسؤول للمجموعة"
    else -> action.replace("_", " ")
}

private fun formatAuditTime(value: Long): String =
    try {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))
    } catch (_: Exception) {
        value.toString()
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
