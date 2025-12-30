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

// ============ МОДЕЛИ ДАННЫХ ============

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

data class TestResult(
    val phoneNumber: String,
    val expectedAction: String,
    val actualAction: String,
    val timestamp: Long,
    val success: Boolean
)

data class TestScenario(
    val phoneNumber: String,
    val description: String,
    val isThreat: Boolean
)

// ============ УТИЛИТЫ ДЛЯ ФОРМАТИРОВАНИЯ ============

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}

// ============ ФУНКЦИИ АНАЛИЗА ЗВОНКОВ ============

private fun analyzeCallForThreats(phoneNumber: String, mode: AnalysisMode): Boolean {
    return when (mode) {
        AnalysisMode.SMART -> shouldBlockNumber(phoneNumber)
        AnalysisMode.AGGRESSIVE -> shouldBlockNumber(phoneNumber) || isSuspiciousNumber(phoneNumber)
        AnalysisMode.PERMISSIVE -> shouldBlockNumber(phoneNumber) && isHighConfidenceThreat(phoneNumber)
    }
}

private fun shouldBlockNumber(phoneNumber: String): Boolean {
    val digits = phoneNumber.filter { it.isDigit() }

    return when {
        // Повторяющиеся цифры
        digits.matches(Regex("(\\d)\\1{6,}")) -> true

        // Слишком короткий номер
        digits.length < 7 -> true

        // Подозрительные паттерны
        phoneNumber.contains("0000") -> true
        phoneNumber.contains("1111") -> true
        phoneNumber.contains("999") -> true

        // Известные спам-номера
        phoneNumber in knownSpamNumbers() -> true

        else -> false
    }
}

private fun isSuspiciousNumber(phoneNumber: String): Boolean {
    val digits = phoneNumber.filter { it.isDigit() }
    return digits.length == 11 && digits.startsWith("7") && digits[1] in "9".toList()
}

private fun isHighConfidenceThreat(phoneNumber: String): Boolean {
    return phoneNumber in highConfidenceThreats()
}

private fun detectThreatType(phoneNumber: String): ThreatType {
    return when {
        phoneNumber in knownSpamNumbers() -> ThreatType.SPAM
        phoneNumber.contains("0000") || phoneNumber.contains("1111") -> ThreatType.SUSPICIOUS_PATTERN
        phoneNumber.filter { it.isDigit() }.length < 7 -> ThreatType.FRAUD
        else -> ThreatType.BLACKLIST
    }
}

private fun knownSpamNumbers(): List<String> = listOf(
    "+79991111111",
    "+79031112233",
    "+79051111111",
    "+79998887766"
)

private fun highConfidenceThreats(): List<String> = listOf(
    "+79991111111",
    "+712345"
)

// ============ ФУНКЦИИ ДЛЯ ПРИМЕРНЫХ ДАННЫХ ============

private fun getSampleCallRecords(): List<CallRecord> = listOf(
    CallRecord(1, "+79161234567", "Иван Петров", "10:25", "2:15", CallStatus.ALLOWED),
    CallRecord(2, "+79991111111", null, "09:42", "0:00", CallStatus.BLOCKED),
    CallRecord(3, "+74951234567", "Офис", "09:15", "1:30", CallStatus.ALLOWED),
    CallRecord(4, "+712345", null, "08:55", "0:00", CallStatus.BLOCKED),
    CallRecord(5, "+74950000000", null, "08:30", "0:00", CallStatus.BLOCKED),
    CallRecord(6, "+79167654321", "Мария", "08:05", "5:20", CallStatus.ALLOWED)
)

private fun getSampleThreats(): List<ThreatAlert> {
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

private fun getUpdatedCallRecords(): List<CallRecord> = getSampleCallRecords() + listOf(
    CallRecord(7, "+79031112233", null, "11:10", "0:00", CallStatus.BLOCKED),
    CallRecord(8, "+79169998877", "Коллега", "11:05", "3:45", CallStatus.ALLOWED)
)

private fun getInitialTestResults(): List<TestResult> = emptyList()

// ============ ОСНОВНОЕ ПРИЛОЖЕНИЕ ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var isProtectionActive by remember { mutableStateOf(true) }
    var blockedCallsCount by remember { mutableStateOf(127) }
    var analysisMode by remember { mutableStateOf(AnalysisMode.SMART) }
    var recentCalls by remember { mutableStateOf(listOf<CallRecord>()) }
    var threatsDetected by remember { mutableStateOf(listOf<ThreatAlert>()) }
    var isLoading by remember { mutableStateOf(false) }
    var showTestPanel by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf(listOf<TestResult>()) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        recentCalls = getSampleCallRecords()
        threatsDetected = getSampleThreats()
        testResults = getInitialTestResults()
    }

    fun simulateCall(phoneNumber: String, contactName: String? = null) {
        coroutineScope.launch {
            isLoading = true

            // Имитация анализа звонка
            delay(500)

            val isThreat = analyzeCallForThreats(phoneNumber, analysisMode)
            val status = if (isThreat && isProtectionActive) {
                CallStatus.BLOCKED
            } else {
                CallStatus.ALLOWED
            }

            val callRecord = CallRecord(
                id = System.currentTimeMillis(),
                phoneNumber = phoneNumber,
                contactName = contactName,
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                duration = if (status == CallStatus.BLOCKED) "0:00" else "${(1..5).random()}:${(10..59).random()}",
                status = status
            )

            recentCalls = listOf(callRecord) + recentCalls.take(9)

            if (isThreat && isProtectionActive) {
                blockedCallsCount++

                val threatAlert = ThreatAlert(
                    id = System.currentTimeMillis(),
                    phoneNumber = phoneNumber,
                    threatType = detectThreatType(phoneNumber),
                    timestamp = System.currentTimeMillis(),
                    confidence = (80..99).random()
                )

                threatsDetected = listOf(threatAlert) + threatsDetected.take(4)
            }

            // Добавляем результат теста
            val testResult = TestResult(
                phoneNumber = phoneNumber,
                expectedAction = if (shouldBlockNumber(phoneNumber)) "Блокировка" else "Разрешить",
                actualAction = if (status == CallStatus.BLOCKED) "Заблокирован" else "Разрешён",
                timestamp = System.currentTimeMillis(),
                success = (isThreat && status == CallStatus.BLOCKED) || (!isThreat && status == CallStatus.ALLOWED)
            )

            testResults = listOf(testResult) + testResults.take(9)

            isLoading = false
        }
    }

    fun runComprehensiveTest() {
        coroutineScope.launch {
            isLoading = true

            // Тестовые сценарии
            val testScenarios = listOf(
                TestScenario("+79161234567", "Нормальный номер", false),
                TestScenario("+79991111111", "Спам-номер (1111111)", true),
                TestScenario("+712345", "Короткий номер", true),
                TestScenario("+74950000000", "Паттерн 0000", true),
                TestScenario("+79031234567", "Неизвестный номер", false),
                TestScenario("+74951234567", "Телефон банка", false),
                TestScenario("+79998887766", "Подозрительный номер", true)
            )

            testResults = emptyList()

            for ((index, scenario) in testScenarios.withIndex()) {
                delay(800)

                simulateCall(scenario.phoneNumber, scenario.description)

                if (index < testScenarios.size - 1) {
                    delay(300)
                }
            }

            isLoading = false
        }
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
                    // Кнопка тестовой панели
                    IconButton(onClick = { showTestPanel = !showTestPanel }) {
                        Text(if (showTestPanel) "📋" else "🧪", style = MaterialTheme.typography.bodyLarge)
                    }
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
                    Text("Анализ вызова...", style = MaterialTheme.typography.bodyMedium)
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
                // Тестовая панель (если включена)
                if (showTestPanel) {
                    item {
                        TestPanelSection(
                            isProtectionActive = isProtectionActive,
                            onRunSingleTest = { phoneNumber, description ->
                                simulateCall(phoneNumber, description)
                            },
                            onRunComprehensiveTest = { runComprehensiveTest() },
                            testResults = testResults
                        )
                    }
                }

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
                                runComprehensiveTest()
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

// ============ КОМПОНЕНТЫ ИНТЕРФЕЙСА ============

@Composable
fun TestPanelSection(
    isProtectionActive: Boolean,
    onRunSingleTest: (String, String) -> Unit,
    onRunComprehensiveTest: () -> Unit,
    testResults: List<TestResult>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🧪 Тестирование системы",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Badge(
                    containerColor = if (isProtectionActive)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                ) {
                    Text(if (isProtectionActive) "ВКЛ" else "ВЫКЛ")
                }
            }

            Text(
                "Протестируйте работу системы с разными типами номеров",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Быстрые тесты
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Быстрые тесты:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TestButton(
                        phoneNumber = "+79161234567",
                        description = "Нормальный",
                        expectedResult = "✅",
                        onClick = onRunSingleTest
                    )

                    TestButton(
                        phoneNumber = "+79991111111",
                        description = "Спам",
                        expectedResult = "⛔",
                        onClick = onRunSingleTest
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TestButton(
                        phoneNumber = "+712345",
                        description = "Короткий",
                        expectedResult = "⛔",
                        onClick = onRunSingleTest
                    )

                    TestButton(
                        phoneNumber = "+74950000000",
                        description = "Паттерн 0000",
                        expectedResult = "⛔",
                        onClick = onRunSingleTest
                    )
                }

                // Комплексный тест
                FilledTonalButton(
                    onClick = onRunComprehensiveTest,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔬")
                        Text("Запустить комплексный тест", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Результаты тестов
            if (testResults.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Результаты тестов:",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            "${testResults.count { it.success }}/${testResults.size} успешно",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.height(120.dp)
                    ) {
                        items(testResults.take(5)) { result ->
                            TestResultItem(result = result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TestButton(
    phoneNumber: String,
    description: String,
    expectedResult: String,
    onClick: (String, String) -> Unit
) {
    OutlinedButton(
        onClick = { onClick(phoneNumber, description) },
        modifier = Modifier.fillMaxWidth(), // Убрали weight
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                phoneNumber.takeLast(7),
                style = MaterialTheme.typography.labelMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(description)
                Text(expectedResult)
            }
        }
    }
}

@Composable
fun TestResultItem(result: TestResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (result.success)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    result.phoneNumber,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    "${result.expectedAction} → ${result.actualAction}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (result.success) "✅" else "❌",
                style = MaterialTheme.typography.bodyLarge
            )
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

        // Статистика в виде простых колонок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("📅", style = MaterialTheme.typography.headlineSmall)
                Text("12", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("сегодня", style = MaterialTheme.typography.labelSmall)
                Text("вызовов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("🛡️", style = MaterialTheme.typography.headlineSmall)
                Text("5", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("блокировано", style = MaterialTheme.typography.labelSmall)
                Text("угроз", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("📈", style = MaterialTheme.typography.headlineSmall)
                Text("98%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("эффективность", style = MaterialTheme.typography.labelSmall)
                Text("точность", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
            // Кнопка включения/выключения защиты
            Button(
                onClick = onToggleProtection,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isProtectionActive)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(if (isProtectionActive) "⏹️" else "▶️")
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            if (isProtectionActive) "Выключить" else "Включить",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            "Защиту",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Кнопка тестирования
            OutlinedButton(
                onClick = onRunScan,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🧪")
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "Протестировать",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            "систему",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
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

            // Первая строка с двумя элементами
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(
                    title = "База данных",
                    value = "Обновлена сегодня",
                    icon = "💾",
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                InfoItem(
                    title = "Версия",
                    value = "2.1.4",
                    icon = "🔢",
                    modifier = Modifier.weight(1f)
                )
            }

            // Вторая строка с двумя элементами
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(
                    title = "Анализов",
                    value = "1,247",
                    icon = "📊",
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                InfoItem(
                    title = "Без сбоев",
                    value = "30 дней",
                    icon = "✅",
                    modifier = Modifier.weight(1f)
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