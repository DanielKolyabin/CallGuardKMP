package com.example.callguard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var isProtectionActive by remember { mutableStateOf(true) }
    var blockedCallsCount by remember { mutableStateOf(127) }
    var analysisMode by remember { mutableStateOf(AnalysisMode.SMART) }
    var recentCalls by remember { mutableStateOf(listOf<CallRecord>()) }
    var threatsDetected by remember { mutableStateOf(listOf<ThreatAlert>()) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        recentCalls = getSampleCallRecords()
        threatsDetected = getSampleThreats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🛡️", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "CallGuard Pro",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            delay(800)
                            recentCalls = getUpdatedCallRecords()
                            isLoading = false
                        }
                    }) {
                        Text("🔄", style = MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick = { /* Открыть настройки */ }) {
                        Text("⚙️", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Text("🏠") },
                    label = { Text("Главная") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Text("📊") },
                    label = { Text("Аналитика") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Text("📋") },
                    label = { Text("История") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Text("👤") },
                    label = { Text("Профиль") }
                )
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Анализ вызовов...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    HeaderSection(isProtectionActive, blockedCallsCount)
                }

                item {
                    QuickActionsSection(
                        isProtectionActive = isProtectionActive,
                        onToggleProtection = { isProtectionActive = !isProtectionActive },
                        onRunScan = {
                            coroutineScope.launch {
                                isLoading = true
                                delay(1200)
                                blockedCallsCount += 3
                                threatsDetected = threatsDetected + ThreatAlert(
                                    id = System.currentTimeMillis(),
                                    phoneNumber = "+74951234567",
                                    threatType = ThreatType.SUSPICIOUS_PATTERN,
                                    timestamp = System.currentTimeMillis(),
                                    confidence = 87
                                )
                                isLoading = false
                            }
                        }
                    )
                }

                item {
                    AnalysisModesSection(
                        currentMode = analysisMode,
                        onModeSelected = { analysisMode = it }
                    )
                }

                if (threatsDetected.isNotEmpty()) {
                    item {
                        ThreatsSection(threats = threatsDetected)
                    }
                }

                item {
                    RecentCallsSection(calls = recentCalls)
                }

                item {
                    SystemInfoSection()
                }
            }
        }
    }
}

@Composable
fun HeaderSection(isActive: Boolean, blockedCount: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Статус защиты",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isActive) "АКТИВНА" else "ВЫКЛЮЧЕНА",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                    )
                    Text(
                        if (isActive) "✅ Все системы работают" else "⚠️ Защита отключена",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        blockedCount.toString(),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }

        // Простая сетка без weight
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatItem(
                    title = "Сегодня",
                    value = "12",
                    subtitle = "вызовов",
                    icon = "📅"
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatItem(
                    title = "Блокировано",
                    value = "5",
                    subtitle = "угроз",
                    icon = "🛡️"
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatItem(
                    title = "Эффектив.",
                    value = "98%",
                    subtitle = "точность",
                    icon = "📈"
                )
            }
        }
    }
}

@Composable
fun StatItem(
    title: String,
    value: String,
    subtitle: String,
    icon: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, style = MaterialTheme.typography.headlineSmall)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            title,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
fun QuickActionsSection(
    isProtectionActive: Boolean,
    onToggleProtection: () -> Unit,
    onRunScan: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Быстрые действия",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                // Исправлено: используем fillMaxWidth() с weight
                modifier = Modifier.fillMaxWidth().weight(1f),
                title = if (isProtectionActive) "Выключить" else "Включить",
                subtitle = if (isProtectionActive) "Защита активна" else "Защита выключена",
                icon = if (isProtectionActive) "⏹️" else "▶️",
                isPrimary = isProtectionActive,
                onClick = onToggleProtection
            )

            ActionButton(
                // Исправлено: используем fillMaxWidth() с weight
                modifier = Modifier.fillMaxWidth().weight(1f),
                title = "Сканировать",
                subtitle = "Проверить вызовы",
                icon = "🔍",
                isPrimary = false,
                onClick = onRunScan
            )
        }
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: String,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isPrimary)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.bodyLarge)
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AnalysisModesSection(
    currentMode: AnalysisMode,
    onModeSelected: (AnalysisMode) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Режим анализа",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalysisModeChip(
                mode = AnalysisMode.SMART,
                isSelected = currentMode == AnalysisMode.SMART,
                onClick = onModeSelected
            )
            AnalysisModeChip(
                mode = AnalysisMode.AGGRESSIVE,
                isSelected = currentMode == AnalysisMode.AGGRESSIVE,
                onClick = onModeSelected
            )
            AnalysisModeChip(
                mode = AnalysisMode.PERMISSIVE,
                isSelected = currentMode == AnalysisMode.PERMISSIVE,
                onClick = onModeSelected
            )
        }
    }
}

@Composable
fun AnalysisModeChip(
    mode: AnalysisMode,
    isSelected: Boolean,
    onClick: (AnalysisMode) -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = { onClick(mode) },
        label = {
            Text(mode.displayName, style = MaterialTheme.typography.labelMedium)
        },
        leadingIcon = if (isSelected) {
            {
                Text("✓", style = MaterialTheme.typography.bodyMedium)
            }
        } else null
    )
}

@Composable
fun ThreatsSection(threats: List<ThreatAlert>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Обнаруженные угрозы",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Badge(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(threats.size.toString())
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(200.dp)
        ) {
            items(threats) { threat ->
                ThreatItem(threat = threat)
            }
        }
    }
}

@Composable
fun ThreatItem(threat: ThreatAlert) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (threat.threatType) {
                        ThreatType.SPAM -> "⚠️"
                        ThreatType.FRAUD -> "🚫"
                        ThreatType.SUSPICIOUS_PATTERN -> "🔍"
                        ThreatType.BLACKLIST -> "⛔"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Исправлено: используем Modifier.weight(1f) вместо .weight(1f)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    threat.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    threat.threatType.displayName,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    formatTimestamp(threat.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White
            ) {
                Text("${threat.confidence}%")
            }
        }
    }
}

@Composable
fun RecentCallsSection(calls: List<CallRecord>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Последние вызовы",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            TextButton(onClick = { }) {
                Text("Все", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(200.dp)
        ) {
            items(calls) { call ->
                CallRecordItem(call = call)
            }
        }
    }
}

@Composable
fun CallRecordItem(call: CallRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (call.status) {
                            CallStatus.BLOCKED -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            CallStatus.ALLOWED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            CallStatus.MISSED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (call.status) {
                        CallStatus.BLOCKED -> "⛔"
                        CallStatus.ALLOWED -> "📞"
                        CallStatus.MISSED -> "❌"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Исправлено: используем Modifier.weight(1f) вместо .weight(1f)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    call.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    call.contactName ?: "Неизвестный номер",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    call.time,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    call.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SystemInfoSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Системная информация",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Исправлено: используем Modifier.weight(1f) вместо .weight(1f)
                InfoItem(
                    modifier = Modifier.weight(1f),
                    title = "База данных",
                    value = "Обновлена сегодня",
                    icon = "💾"
                )
                InfoItem(
                    modifier = Modifier.weight(1f),
                    title = "Версия",
                    value = "2.1.4",
                    icon = "🔢"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Исправлено: используем Modifier.weight(1f) вместо .weight(1f)
                InfoItem(
                    modifier = Modifier.weight(1f),
                    title = "Анализов",
                    value = "1,247",
                    icon = "📊"
                )
                InfoItem(
                    modifier = Modifier.weight(1f),
                    title = "Без сбоев",
                    value = "30 дней",
                    icon = "✅"
                )
            }
        }
    }
}

@Composable
fun InfoItem(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: String
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(icon, style = MaterialTheme.typography.bodyLarge)
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

// Утилита для форматирования времени
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}

// Модели данных
enum class AnalysisMode(val displayName: String) {
    SMART("Умный"),
    AGGRESSIVE("Агрессивный"),
    PERMISSIVE("Разрешающий")
}

enum class ThreatType(val displayName: String) {
    SPAM("Спам"),
    FRAUD("Мошенничество"),
    SUSPICIOUS_PATTERN("Подозрительный паттерн"),
    BLACKLIST("Черный список")
}

enum class CallStatus {
    BLOCKED, ALLOWED, MISSED
}

data class ThreatAlert(
    val id: Long,
    val phoneNumber: String,
    val threatType: ThreatType,
    val timestamp: Long,
    val confidence: Int
)

data class CallRecord(
    val id: Long,
    val phoneNumber: String,
    val contactName: String?,
    val time: String,
    val duration: String,
    val status: CallStatus
)

// Примеры данных
fun getSampleCallRecords(): List<CallRecord> = listOf(
    CallRecord(1, "+79161234567", "Иван Петров", "10:25", "2:15", CallStatus.ALLOWED),
    CallRecord(2, "+79991111111", null, "09:42", "0:00", CallStatus.BLOCKED),
    CallRecord(3, "+74951234567", "Офис", "09:15", "1:30", CallStatus.ALLOWED),
    CallRecord(4, "+712345", null, "08:55", "0:00", CallStatus.BLOCKED),
    CallRecord(5, "+74950000000", null, "08:30", "0:00", CallStatus.BLOCKED),
    CallRecord(6, "+79167654321", "Мария", "08:05", "5:20", CallStatus.ALLOWED)
)

fun getSampleThreats(): List<ThreatAlert> {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.HOUR, -2)
    val twoHoursAgo = calendar.timeInMillis

    calendar.add(Calendar.HOUR, -2)
    val fourHoursAgo = calendar.timeInMillis

    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val oneDayAgo = calendar.timeInMillis

    return listOf(
        ThreatAlert(1, "+79991111111", ThreatType.SPAM, twoHoursAgo, 95),
        ThreatAlert(2, "+712345", ThreatType.FRAUD, fourHoursAgo, 88),
        ThreatAlert(3, "+74950000000", ThreatType.SUSPICIOUS_PATTERN, oneDayAgo, 92)
    )
}

fun getUpdatedCallRecords(): List<CallRecord> = getSampleCallRecords() + listOf(
    CallRecord(7, "+79031112233", null, "11:10", "0:00", CallStatus.BLOCKED),
    CallRecord(8, "+79169998877", "Коллега", "11:05", "3:45", CallStatus.ALLOWED)
)