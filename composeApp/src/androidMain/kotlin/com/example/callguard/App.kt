package com.example.callguard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

enum class AnalysisMode(val displayName: String, val description: String) {
    SMART("Умный", "Баланс между защитой и удобством"),
    AGGRESSIVE("Агрессивный", "Блокирует все подозрительное"),
    PERMISSIVE("Разрешающий", "Блокирует только явные угрозы")
}

enum class ThreatType(val displayName: String) {
    SPAM("Спам"),
    FRAUD("Мошенничество"),
    SUSPICIOUS_PATTERN("Подозрительный паттерн"),
    BLACKLIST("Черный список"),
    INTERNATIONAL("Международный спам"),
    ANONYMOUS("Анонимный вызов")
}

enum class CallStatus {
    BLOCKED, ALLOWED, MISSED
}

enum class BlockReason(val displayName: String, val description: String) {
    REPEATING_DIGITS("Повторяющиеся цифры", "Номер состоит из одинаковых цифр"),
    SHORT_NUMBER("Короткий номер", "Менее 7 цифр"),
    SUSPICIOUS_PATTERN("Подозрительный паттерн", "Содержит 0000, 1111, 999"),
    KNOWN_SPAM("Известный спам", "В черном списке"),
    PRIVATE_NUMBER("Скрытый номер", "Скрытый или анонимный"),
    INTERNATIONAL_SCAM("Международный спам", "Подозрительный международный номер"),
    SEQUENTIAL_NUMBER("Последовательность", "Цифры по порядку или в обратном"),
    MASS_DIALING("Массовая рассылка", "Номер для массовых звонков")
}

data class ThreatAlert(
    val id: Long,
    val phoneNumber: String,
    val threatType: ThreatType,
    val timestamp: Long,
    val confidence: Int,
    val blockReason: BlockReason
)

data class CallRecord(
    val id: Long,
    val phoneNumber: String,
    val contactName: String?,
    val time: String,
    val duration: String,
    val status: CallStatus,
    val blockReason: BlockReason?
)

data class TestResult(
    val id: Long,
    val phoneNumber: String,
    val description: String,
    val expectedAction: String,
    val actualAction: String,
    val timestamp: Long,
    val success: Boolean,
    val details: String,
    val blockReason: BlockReason?,
    val category: String
)

data class TestScenario(
    val id: Int,
    val phoneNumber: String,
    val description: String,
    val category: String,
    val expectedAction: String,
    val blockReason: BlockReason?,
    val details: String,
    val difficulty: Int // 1-легкий, 2-средний, 3-сложный
)

// ============ УТИЛИТЫ ============

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    return formatter.format(date)
}

private fun formatTimeShort(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(date)
}

// ============ ФУНКЦИИ АНАЛИЗА ============

private fun analyzeCallForThreats(phoneNumber: String, mode: AnalysisMode): Pair<Boolean, BlockReason?> {
    return when (mode) {
        AnalysisMode.SMART -> analyzeSmartMode(phoneNumber)
        AnalysisMode.AGGRESSIVE -> analyzeAggressiveMode(phoneNumber)
        AnalysisMode.PERMISSIVE -> analyzePermissiveMode(phoneNumber)
    }
}

private fun analyzeSmartMode(phoneNumber: String): Pair<Boolean, BlockReason?> {
    val digits = phoneNumber.filter { it.isDigit() }

    return when {
        digits.matches(Regex("(\\d)\\1{6,}")) ->
            true to BlockReason.REPEATING_DIGITS

        digits.length < 7 ->
            true to BlockReason.SHORT_NUMBER

        phoneNumber.contains("0000") || phoneNumber.contains("1111") || phoneNumber.contains("999") ->
            true to BlockReason.SUSPICIOUS_PATTERN

        phoneNumber in getKnownSpamNumbers() ->
            true to BlockReason.KNOWN_SPAM

        phoneNumber == "unknown" || phoneNumber == "private" || phoneNumber.contains("#31#") ->
            true to BlockReason.PRIVATE_NUMBER

        phoneNumber.startsWith("+1") && phoneNumber.contains("555") ->
            true to BlockReason.INTERNATIONAL_SCAM

        else -> false to null
    }
}

private fun analyzeAggressiveMode(phoneNumber: String): Pair<Boolean, BlockReason?> {
    val (shouldBlock, reason) = analyzeSmartMode(phoneNumber)

    if (shouldBlock) return true to reason

    val digits = phoneNumber.filter { it.isDigit() }

    return when {
        isSequentialNumber(digits) ->
            true to BlockReason.SEQUENTIAL_NUMBER

        phoneNumber.startsWith("+7900") ->
            true to BlockReason.MASS_DIALING

        phoneNumber.startsWith("+") && !phoneNumber.startsWith("+7") && !phoneNumber.startsWith("+1") ->
            true to BlockReason.INTERNATIONAL_SCAM

        digits.matches(Regex("(\\d{2})\\1{3,}")) ->
            true to BlockReason.SUSPICIOUS_PATTERN

        else -> false to null
    }
}

private fun analyzePermissiveMode(phoneNumber: String): Pair<Boolean, BlockReason?> {
    val digits = phoneNumber.filter { it.isDigit() }

    return when {
        phoneNumber in getHighRiskSpamNumbers() ->
            true to BlockReason.KNOWN_SPAM

        digits.matches(Regex("(\\d)\\1{8,}")) ->
            true to BlockReason.REPEATING_DIGITS

        digits.length < 5 ->
            true to BlockReason.SHORT_NUMBER

        else -> false to null
    }
}

private fun isSequentialNumber(digits: String): Boolean {
    if (digits.length < 7) return false

    var isAscending = true
    for (i in 1 until digits.length) {
        if (digits[i].digitToInt() != digits[i-1].digitToInt() + 1) {
            isAscending = false
            break
        }
    }

    var isDescending = true
    for (i in 1 until digits.length) {
        if (digits[i].digitToInt() != digits[i-1].digitToInt() - 1) {
            isDescending = false
            break
        }
    }

    return isAscending || isDescending
}

private fun getKnownSpamNumbers(): List<String> = listOf(
    "+79991111111", "+79031112233", "+79051111111", "+79998887766",
    "+74951230000", "+79001234567", "+79069876543", "+79025556677",
    "+79034445566", "+79017778899"
)

private fun getHighRiskSpamNumbers(): List<String> = listOf(
    "+79991111111", "+712345", "+79031112233"
)

private fun detectThreatType(blockReason: BlockReason?): ThreatType {
    return when (blockReason) {
        BlockReason.REPEATING_DIGITS, BlockReason.SEQUENTIAL_NUMBER -> ThreatType.SUSPICIOUS_PATTERN
        BlockReason.SHORT_NUMBER -> ThreatType.FRAUD
        BlockReason.KNOWN_SPAM, BlockReason.MASS_DIALING -> ThreatType.BLACKLIST
        BlockReason.PRIVATE_NUMBER -> ThreatType.ANONYMOUS
        BlockReason.INTERNATIONAL_SCAM -> ThreatType.INTERNATIONAL
        BlockReason.SUSPICIOUS_PATTERN -> ThreatType.SPAM
        null -> ThreatType.SPAM
    }
}

// ============ ТЕСТОВЫЕ СЦЕНАРИИ ============

private fun getTestScenarios(): List<TestScenario> = listOf(
    TestScenario(1, "+79161234567", "Личный номер", "Нормальный", "Разрешить", null, "Нормальный российский номер", 1),
    TestScenario(2, "+74957775533", "Московский номер", "Нормальный", "Разрешить", null, "Городской номер Москвы", 1),
    TestScenario(3, "+78002000600", "Служба поддержки", "Нормальный", "Разрешить", null, "Бесплатный номер", 1),
    TestScenario(4, "+74952123456", "Бизнес номер", "Нормальный", "Разрешить", null, "Корпоративный номер", 1),

    TestScenario(5, "+79991111111", "Повторяющиеся 1", "Очевидный спам", "Блокировка", BlockReason.REPEATING_DIGITS, "7 повторяющихся единиц", 1),
    TestScenario(6, "+72222222222", "Повторяющиеся 2", "Очевидный спам", "Блокировка", BlockReason.REPEATING_DIGITS, "Повторяющиеся двойки", 1),
    TestScenario(7, "+712345", "Короткий номер", "Очевидный спам", "Блокировка", BlockReason.SHORT_NUMBER, "Всего 6 цифр", 1),
    TestScenario(8, "+74951230000", "Нулевой паттерн", "Очевидный спам", "Блокировка", BlockReason.SUSPICIOUS_PATTERN, "Известный спам-номер", 1),

    TestScenario(9, "+74950000000", "Много нулей", "Подозрительный", "Блокировка", BlockReason.SUSPICIOUS_PATTERN, "Паттерн 0000", 2),
    TestScenario(10, "+79161111111", "Много единиц", "Подозрительный", "Блокировка", BlockReason.SUSPICIOUS_PATTERN, "Паттерн 1111", 2),
    TestScenario(11, "+79039999999", "Много девяток", "Подозрительный", "Блокировка", BlockReason.SUSPICIOUS_PATTERN, "Паттерн 999", 2),
    TestScenario(12, "+79034445566", "Повтор пар", "Подозрительный", "Блокировка", BlockReason.SUSPICIOUS_PATTERN, "Повторяющиеся пары цифр", 2),

    TestScenario(13, "+79031112233", "Из черного списка", "Известный спам", "Блокировка", BlockReason.KNOWN_SPAM, "Номер в черном списке", 2),
    TestScenario(14, "+79051111111", "Спам-рассылка", "Известный спам", "Блокировка", BlockReason.KNOWN_SPAM, "Массовая рассылка", 2),
    TestScenario(15, "+79025556677", "Рекламный номер", "Известный спам", "Блокировка", BlockReason.KNOWN_SPAM, "Рекламные звонки", 2),

    TestScenario(16, "unknown", "Скрытый номер", "Анонимный", "Блокировка", BlockReason.PRIVATE_NUMBER, "Скрытый номер", 3),
    TestScenario(17, "#31#+79161234567", "Скрытый вызов", "Анонимный", "Блокировка", BlockReason.PRIVATE_NUMBER, "С вызовом через #31#", 3),

    TestScenario(18, "+15551234567", "Американский", "Международный", "Разрешить", null, "Номер из США", 2),
    TestScenario(19, "+15555555555", "Подозрительный US", "Международный", "Блокировка", BlockReason.INTERNATIONAL_SCAM, "Подозрительный американский", 2),
    TestScenario(20, "+441234567890", "Великобритания", "Международный", "Разрешить", null, "Номер из UK", 2),

    TestScenario(21, "+79161234567", "Последовательность", "Паттерн", "Блокировка", BlockReason.SEQUENTIAL_NUMBER, "Цифры по порядку 1234567", 3),
    TestScenario(22, "+79169876543", "Обратная посл.", "Паттерн", "Блокировка", BlockReason.SEQUENTIAL_NUMBER, "Цифры в обратном порядке", 3),

    TestScenario(23, "+79001234567", "Массовая рассылка", "Массовый спам", "Блокировка", BlockReason.MASS_DIALING, "Диапазон для рассылок", 2),
    TestScenario(24, "+79017778899", "Call-центр", "Массовый спам", "Блокировка", BlockReason.MASS_DIALING, "Номер call-центра", 2)
)

// ============ ОБРАЗЦЫ ДАННЫХ ============

private fun getSampleCallRecords(): List<CallRecord> = listOf(
    CallRecord(1, "+79161234567", "Иван Петров", "10:25", "2:15", CallStatus.ALLOWED, null),
    CallRecord(2, "+79991111111", null, "09:42", "0:00", CallStatus.BLOCKED, BlockReason.REPEATING_DIGITS),
    CallRecord(3, "+74951234567", "Офис", "09:15", "1:30", CallStatus.ALLOWED, null),
    CallRecord(4, "+712345", null, "08:55", "0:00", CallStatus.BLOCKED, BlockReason.SHORT_NUMBER),
    CallRecord(5, "+74950000000", null, "08:30", "0:00", CallStatus.BLOCKED, BlockReason.SUSPICIOUS_PATTERN),
    CallRecord(6, "+79167654321", "Мария", "08:05", "5:20", CallStatus.ALLOWED, null)
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
        ThreatAlert(1, "+79991111111", ThreatType.SUSPICIOUS_PATTERN, twoHoursAgo, 95, BlockReason.REPEATING_DIGITS),
        ThreatAlert(2, "+712345", ThreatType.FRAUD, fourHoursAgo, 88, BlockReason.SHORT_NUMBER),
        ThreatAlert(3, "+74950000000", ThreatType.SPAM, oneDayAgo, 92, BlockReason.SUSPICIOUS_PATTERN)
    )
}

private fun getUpdatedCallRecords(): List<CallRecord> = getSampleCallRecords() + listOf(
    CallRecord(7, "+79031112233", null, "11:10", "0:00", CallStatus.BLOCKED, BlockReason.KNOWN_SPAM),
    CallRecord(8, "+79169998877", "Коллега", "11:05", "3:45", CallStatus.ALLOWED, null)
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
    var selectedTestCategory by remember { mutableStateOf("Все") }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        recentCalls = getSampleCallRecords()
        threatsDetected = getSampleThreats()
    }

    val testCategories = listOf("Все", "Нормальный", "Очевидный спам", "Подозрительный",
        "Известный спам", "Анонимный", "Международный", "Паттерн", "Массовый спам")

    val filteredScenarios = if (selectedTestCategory == "Все") {
        getTestScenarios()
    } else {
        getTestScenarios().filter { it.category == selectedTestCategory }
    }

    fun simulateCall(scenario: TestScenario) {
        coroutineScope.launch {
            isLoading = true
            delay(300)

            val (isThreat, blockReason) = analyzeCallForThreats(scenario.phoneNumber, analysisMode)
            val status = if (isThreat && isProtectionActive) {
                CallStatus.BLOCKED
            } else {
                CallStatus.ALLOWED
            }

            val callRecord = CallRecord(
                id = System.currentTimeMillis(),
                phoneNumber = scenario.phoneNumber,
                contactName = scenario.description,
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                duration = if (status == CallStatus.BLOCKED) "0:00" else "${(1..3).random()}:${(10..45).random()}",
                status = status,
                blockReason = blockReason
            )

            recentCalls = listOf(callRecord) + recentCalls.take(9)

            if (isThreat && isProtectionActive && blockReason != null) {
                blockedCallsCount++

                val threatAlert = ThreatAlert(
                    id = System.currentTimeMillis(),
                    phoneNumber = scenario.phoneNumber,
                    threatType = detectThreatType(blockReason),
                    timestamp = System.currentTimeMillis(),
                    confidence = (85..99).random(),
                    blockReason = blockReason
                )

                threatsDetected = listOf(threatAlert) + threatsDetected.take(4)
            }

            val success = when {
                !isProtectionActive -> status == CallStatus.ALLOWED
                else -> {
                    when {
                        scenario.blockReason != null -> isThreat && status == CallStatus.BLOCKED
                        else -> !isThreat && status == CallStatus.ALLOWED
                    }
                }
            }

            val testResult = TestResult(
                id = System.currentTimeMillis(),
                phoneNumber = scenario.phoneNumber,
                description = scenario.description,
                expectedAction = if (scenario.blockReason != null) "Блокировка" else "Разрешить",
                actualAction = if (status == CallStatus.BLOCKED) "Заблокирован" else "Разрешён",
                timestamp = System.currentTimeMillis(),
                success = success,
                details = scenario.details,
                blockReason = blockReason,
                category = scenario.category
            )

            testResults = listOf(testResult) + testResults.take(14)
            isLoading = false
        }
    }

    fun runCategoryTest(category: String) {
        coroutineScope.launch {
            isLoading = true
            val scenarios = getTestScenarios().filter { it.category == category }

            for ((index, scenario) in scenarios.withIndex()) {
                delay(400)
                simulateCall(scenario)

                if (index < scenarios.size - 1) {
                    delay(200)
                }
            }
            isLoading = false
        }
    }

    fun runComprehensiveTest() {
        coroutineScope.launch {
            isLoading = true
            testResults = emptyList()
            val scenarios = getTestScenarios()

            for ((index, scenario) in scenarios.withIndex()) {
                delay(350)
                simulateCall(scenario)

                if (index < scenarios.size - 1) {
                    delay(150)
                }
            }
            isLoading = false
        }
    }

    fun runDifficultyTest(difficulty: Int) {
        coroutineScope.launch {
            isLoading = true
            val scenarios = getTestScenarios().filter { it.difficulty == difficulty }

            for ((index, scenario) in scenarios.withIndex()) {
                delay(500)
                simulateCall(scenario)

                if (index < scenarios.size - 1) {
                    delay(250)
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
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "CG",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
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
                    IconButton(onClick = { showTestPanel = !showTestPanel }) {
                        Text(
                            if (showTestPanel) "Главная" else "Тесты",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            delay(800)
                            recentCalls = getUpdatedCallRecords()
                            isLoading = false
                        }
                    }) {
                        Text("Обновить", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !showTestPanel,
                    onClick = { showTestPanel = false },
                    icon = {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏠")
                        }
                    },
                    label = { Text("Главная") }
                )
                NavigationBarItem(
                    selected = showTestPanel,
                    onClick = { showTestPanel = true },
                    icon = {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🧪")
                        }
                    },
                    label = { Text("Тесты") }
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
        } else if (showTestPanel) {
            AdvancedTestPanel(
                isProtectionActive = isProtectionActive,
                analysisMode = analysisMode,
                selectedCategory = selectedTestCategory,
                categories = testCategories,
                onCategorySelected = { selectedTestCategory = it },
                onRunSingleTest = { scenario -> simulateCall(scenario) },
                onRunCategoryTest = { category -> runCategoryTest(category) },
                onRunComprehensiveTest = { runComprehensiveTest() },
                onRunDifficultyTest = { difficulty -> runDifficultyTest(difficulty) },
                testResults = testResults,
                testScenarios = filteredScenarios,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
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

// ============ РАСШИРЕННАЯ ТЕСТОВАЯ ПАНЕЛЬ ============

@Composable
fun AdvancedTestPanel(
    isProtectionActive: Boolean,
    analysisMode: AnalysisMode,
    selectedCategory: String,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
    onRunSingleTest: (TestScenario) -> Unit,
    onRunCategoryTest: (String) -> Unit,
    onRunComprehensiveTest: () -> Unit,
    onRunDifficultyTest: (Int) -> Unit,
    testResults: List<TestResult>,
    testScenarios: List<TestScenario>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Лаборатория тестирования",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Badge(
                            containerColor = if (isProtectionActive)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        ) {
                            Text(if (isProtectionActive) "ЗАЩИТА ВКЛ" else "ЗАЩИТА ВЫКЛ")
                        }
                    }

                    Text(
                        "Тестируйте алгоритмы анализа с 24+ сценариями",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Режим:", style = MaterialTheme.typography.labelSmall)
                            Text(analysisMode.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        val successRate = if (testResults.isNotEmpty()) {
                            testResults.count { it.success } * 100 / testResults.size
                        } else { 0 }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Успешность:", style = MaterialTheme.typography.labelSmall)
                            Text("$successRate%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Категории тестов:",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = category == selectedCategory,
                        onClick = { onCategorySelected(category) },
                        label = {
                            Text(category, style = MaterialTheme.typography.labelMedium)
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Быстрые тесты",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onRunDifficultyTest(1) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        ) {
                            Text("Легкие")
                        }

                        Button(
                            onClick = { onRunDifficultyTest(2) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                            )
                        ) {
                            Text("Средние")
                        }

                        Button(
                            onClick = { onRunDifficultyTest(3) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                            )
                        ) {
                            Text("Сложные")
                        }
                    }

                    FilledTonalButton(
                        onClick = onRunComprehensiveTest,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text("Полное тестирование (24 сценария)", style = MaterialTheme.typography.labelLarge)
                    }

                    if (selectedCategory != "Все") {
                        OutlinedButton(
                            onClick = { onRunCategoryTest(selectedCategory) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Тест категории: $selectedCategory", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Сценарии тестирования (${testScenarios.size}):",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        items(testScenarios.chunked(2)) { rowScenarios ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowScenarios.forEach { scenario ->
                    Box(modifier = Modifier.weight(1f)) {
                        TestScenarioCard(
                            scenario = scenario,
                            onClick = { onRunSingleTest(scenario) }
                        )
                    }
                }

                if (rowScenarios.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (testResults.isNotEmpty()) {
            item {
                TestResultsSection(testResults = testResults)
            }
        }
    }
}

@Composable
fun TestScenarioCard(
    scenario: TestScenario,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (scenario.blockReason != null)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (scenario.blockReason != null) "БЛОК" else "ОК",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (scenario.blockReason != null)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(scenario.difficulty) {
                        Text("•", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Text(
                scenario.phoneNumber.take(12),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )

            Text(
                scenario.description,
                style = MaterialTheme.typography.bodySmall
            )

            Divider(modifier = Modifier.fillMaxWidth(), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (scenario.blockReason != null) "Блокировка" else "Разрешить",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (scenario.blockReason != null)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )

                Text(
                    scenario.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TestResultsSection(testResults: List<TestResult>) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Результаты тестов",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                val successCount = testResults.count { it.success }
                val total = testResults.size
                Badge(
                    containerColor = if (successCount == total)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                ) {
                    Text("$successCount/$total")
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(testResults.take(10)) { result ->
                    TestResultItem(result = result)
                }
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
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
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
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (result.success)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (result.success) "УСП" else "ОШБ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (result.success)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    result.phoneNumber.take(14),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Ожидалось: ${result.expectedAction}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("→", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Факт: ${result.actualAction}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (result.success)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                result.blockReason?.let { reason ->
                    Text(
                        "Причина: ${reason.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                formatTimeShort(result.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============ ОСНОВНЫЕ КОМПОНЕНТЫ ИНТЕРФЕЙСА ============

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
                        if (isActive) "Все системы работают" else "Защита отключена",
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("СЕГОДНЯ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Text("12", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("вызовов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("БЛОКИРОВАНО", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Text("5", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("угроз", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("ЭФФЕКТИВНОСТЬ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Text("98%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
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
            Button(
                onClick = onToggleProtection,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isProtectionActive)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        if (isProtectionActive) "ВЫКЛЮЧИТЬ" else "ВКЛЮЧИТЬ",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        "Защиту",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            OutlinedButton(
                onClick = onRunScan,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "ТЕСТИРОВАТЬ",
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

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalysisMode.values().forEach { mode ->
                AnalysisModeCard(
                    mode = mode,
                    isSelected = currentMode == mode,
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

@Composable
fun AnalysisModeCard(
    mode: AnalysisMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder() else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (mode) {
                        AnalysisMode.SMART -> "У"
                        AnalysisMode.AGGRESSIVE -> "А"
                        AnalysisMode.PERMISSIVE -> "Р"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    mode.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Text("✓", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
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
                    "УГР",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
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
                    "${threat.blockReason.displayName} • ${threat.threatType.displayName}",
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
                        CallStatus.BLOCKED -> "БЛОК"
                        CallStatus.ALLOWED -> "ОК"
                        CallStatus.MISSED -> "НЕТ"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
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
                call.blockReason?.let { reason ->
                    Text(
                        "Причина: ${reason.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Системная информация",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("БАЗА ДАННЫХ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Обновлена",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        "сегодня",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("ВЕРСИЯ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "2.1.4",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        "версия",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("АНАЛИЗОВ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "1,247",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        "анализов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("БЕЗ СБОЕВ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "30 дней",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        "без сбоев",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}