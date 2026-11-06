package com.takeyourbrain.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.takeyourbrain.app.AppContainer
import com.takeyourbrain.app.R
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.block.BlockActivity
import com.takeyourbrain.app.data.FocusSession
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.data.db.BankSession
import com.takeyourbrain.app.data.db.Pass
import com.takeyourbrain.app.data.db.RuleWithIntervals
import com.takeyourbrain.app.domain.BankPricing
import com.takeyourbrain.app.domain.BlockDecision
import com.takeyourbrain.app.domain.BlockInfo
import com.takeyourbrain.app.domain.BlockReason
import com.takeyourbrain.app.domain.BlockWarning
import com.takeyourbrain.app.domain.BlockableElement
import com.takeyourbrain.app.domain.ProtectedApps
import com.takeyourbrain.app.domain.RuleEvaluator
import com.takeyourbrain.app.notifications.Notifications
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Служба специальных возможностей.
 * Узнаёт, какое приложение открыто (событие TYPE_WINDOW_STATE_CHANGED), и пока оно на экране,
 * раз в секунду проверяет его правило: присылает предупреждение незадолго до блокировки,
 * а если правило нарушено — уводит на домашний экран и показывает экран блокировки.
 * Когда экран выключен, проверка не идёт совсем (экономим батарею).
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var container: AppContainer
    private lateinit var powerManager: PowerManager

    // Правила и настройки храним в памяти: они обновляются сами при любом изменении
    private var rules: Map<String, RuleWithIntervals> = emptyMap()
    private var passes: List<Pass> = emptyList()
    private var focus: FocusSession? = null
    private var bankSessions: List<BankSession> = emptyList()
    private var insideBlockedBuyable = false
    private var warningMinutes = SettingsRepository.DEFAULT_WARNING_MINUTES
    private var dumpEnabled = false // отладочный дамп экрана в Logcat (только отладочная сборка)

    // Части приложений (YouTube Shorts): когда проверяли в последний раз и до какого момента не трогаем
    private var lastInsideCheck = 0L
    private var elementCooldownUntil = 0L
    private var lastDump = 0L

    // Сколько сегодня открыты части приложений с лимитом (Shorts), миллисекунды. Время от времени сохраняем
    private var elementUsageDay: LocalDate = LocalDate.now()
    private val elementUsage = HashMap<String, Long>()
    private var lastElementSave = 0L

    private var currentPackage: String? = null // приложение, которое сейчас на экране
    private var monitorJob: Job? = null         // ежесекундная проверка текущего приложения
    private var launcherPackage: String? = null // домашний экран (лаунчер)

    // Кэш: является ли окно (пакет + класс) полноценным экраном приложения (Activity)
    private val activityCache = HashMap<ComponentName, Boolean>()

    // Какие предупреждения уже показаны, чтобы не повторять их каждую секунду
    private val shownWarnings = HashSet<String>()

    // Ушли из приложения со временем из банка: через несколько секунд сессия закончится и остаток вернётся
    private val sessionEndJobs = HashMap<String, Job>()

    // Экран погас — останавливаем проверку; включился — запускаем снова
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    monitorJob?.cancel()
                    leaveApp(currentPackage)
                }
                Intent.ACTION_SCREEN_ON -> currentPackage?.let {
                    returnToApp(it)
                    startMonitoring(it)
                }
            }
        }
    }

    override fun onServiceConnected() {
        container = (application as TakeYourBrainApp).container
        // Служба могла (пере)подключиться посреди работы приложения (обновление, другая служба
        // спец. возможностей): сразу узнаём, что сейчас на экране, а не ждём переключения приложений
        currentPackage = rootInActiveWindow?.packageName?.toString()
        // Настройки событий после подключения снова из XML — включаем нужные заново
        updateContentEvents()
        powerManager = getSystemService(PowerManager::class.java)
        launcherPackage = findLauncherPackage()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            RECEIVER_NOT_EXPORTED
        )
        scope.launch {
            container.rulesRepository.rules.collect { newRules ->
                rules = newRules
                // Правило могли поменять — перезапускаем проверку текущего приложения
                currentPackage?.let { startMonitoring(it) }
                updateContentEvents()
            }
        }
        scope.launch {
            BlockableElement.entries.forEach { elementUsage[it.id] = container.elementUsageRepository.usedToday(it.id) }
        }
        scope.launch {
            container.settingsRepository.debugDump.collect { on ->
                dumpEnabled = on && isDebuggable()
                updateContentEvents()
            }
        }
        scope.launch {
            container.settingsRepository.warningMinutes.collect { warningMinutes = it }
        }
        scope.launch {
            container.passRepository.recentPasses.collect { passes = it }
        }
        scope.launch {
            container.bankRepository.sessions.collect { bankSessions = it }
        }
        scope.launch {
            container.settingsRepository.bankInsideBlockedBuyable.collect { insideBlockedBuyable = it }
        }
        scope.launch {
            container.focusRepository.session.collect { session ->
                focus = session
                // Фокус начали или отменили — перепроверяем текущее приложение
                currentPackage?.let { startMonitoring(it) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        // То же приложение: внутри могли открыться Shorts (и нужен ли отладочный дамп)
        if (pkg == currentPackage) {
            checkInsideApp(pkg)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val cls = event.className?.toString() ?: return
        // Клавиатура, всплывающие окна, шторка уведомлений — это не смена приложения
        if (pkg != launcherPackage && !isActivity(pkg, cls)) return

        if (dumpEnabled) Log.d(NodeDump.TAG, "открыто приложение: $pkg ($cls)")
        leaveApp(currentPackage)
        returnToApp(pkg)
        currentPackage = pkg
        startMonitoring(pkg)
        updateContentEvents()
        checkInsideApp(pkg)
    }

    override fun onInterrupt() {
        // Вызывается, когда система просит прервать "обратную связь". Нам нечего прерывать.
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Проверка внутри открытого приложения: если открыта заблокированная часть (YouTube Shorts) —
     * нажимаем "Назад" и показываем короткое сообщение. У части с лимитом сначала копится время
     * ([elapsedMs] передаёт ежесекундная проверка), закрываем её, когда лимит исчерпан.
     * Здесь же отладочный дамп экрана.
     */
    private fun checkInsideApp(pkg: String, elapsedMs: Long? = null) {
        val now = SystemClock.elapsedRealtime()
        if (dumpEnabled && now - lastDump > DUMP_INTERVAL_MS) {
            lastDump = now
            rootInActiveWindow?.let(NodeDump::log)
        }
        val elements = blockedElements(pkg)
        if (elements.isEmpty() || now < elementCooldownUntil) return
        // События приходят очень часто — проверяем не чаще раза в 300 мс (ежесекундная проверка — всегда)
        if (elapsedMs == null && now - lastInsideCheck < INSIDE_CHECK_MS) return
        lastInsideCheck = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        for ((element, limit) in elements) {
            val opened = element.viewIds.any { id -> root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser } }
            if (!opened) continue
            if (limit != null) {
                val used = addElementTime(element.id, elapsedMs ?: 0L)
                if (used < limit * 60_000L) continue // лимит ещё не исчерпан — смотреть можно
            }
            closeElement(pkg, limitReached = limit != null)
            return
        }
    }

    /** Закрыть часть приложения: "Назад" и короткое сообщение. Следующую проверку чуть откладываем. */
    private fun closeElement(pkg: String, limitReached: Boolean) {
        elementCooldownUntil = SystemClock.elapsedRealtime() + ELEMENT_COOLDOWN_MS
        performGlobalAction(GLOBAL_ACTION_BACK)
        val text = if (limitReached) R.string.element_limit_toast else R.string.element_blocked_toast
        Toast.makeText(this, Notifications.localized(this).getString(text), Toast.LENGTH_SHORT).show()
        scope.launch { container.blockLogRepository.logElement(pkg) }
    }

    /** Добавить [ms] ко времени части [id] за сегодня; в полночь счёт начинается заново. Возвращает итог. */
    private fun addElementTime(id: String, ms: Long): Long {
        val today = LocalDate.now()
        if (today != elementUsageDay) {
            elementUsageDay = today
            elementUsage.clear()
        }
        val total = (elementUsage[id] ?: 0L) + ms
        elementUsage[id] = total
        // Сохраняем не чаще раза в 15 секунд: так счёт переживёт перезапуск
        val now = SystemClock.elapsedRealtime()
        if (ms > 0 && now - lastElementSave > ELEMENT_SAVE_MS) {
            lastElementSave = now
            val snapshot = HashMap(elementUsage)
            scope.launch { container.elementUsageRepository.save(today, snapshot) }
        }
        return total
    }

    /** Заблокированные части приложения [pkg] по его правилу и их лимиты (null — всегда). */
    private fun blockedElements(pkg: String?): List<Pair<BlockableElement, Int?>> =
        rules[pkg]?.rule?.elementLimits.orEmpty().mapNotNull { (id, limit) -> BlockableElement.byId(id)?.let { it to limit } }

    /**
     * События "содержимое окна изменилось" приходят очень часто, поэтому получаем их только когда нужно:
     * в текущем приложении есть заблокированные части или включён отладочный дамп.
     */
    private fun updateContentEvents() {
        val want = dumpEnabled || blockedElements(currentPackage).isNotEmpty()
        // Смотрим на настоящие настройки службы: после переподключения Android возвращает их из XML
        val info = serviceInfo ?: return
        val has = info.eventTypes and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED != 0
        if (want == has) return
        info.eventTypes = if (want) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        } else {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }
        serviceInfo = info
    }

    private fun isDebuggable(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Приложение [pkg] входит в идущий сейчас режим фокуса. */
    private fun isInFocus(pkg: String): Boolean = focus?.let { it.isActive() && pkg in it.apps } == true

    /** Начать ежесекундную проверку приложения [pkg] (если для него есть правило или идёт фокус). */
    private fun startMonitoring(pkg: String) {
        monitorJob?.cancel()
        if (pkg !in rules && !isInFocus(pkg)) return      // проверять нечего
        if (ProtectedApps.isProtected(this, pkg)) return  // себя, звонилку, настройки и т.п. не блокируем никогда
        if (!powerManager.isInteractive) return         // экран выключен — проверим, когда включится

        monitorJob = scope.launch {
            var day = LocalDate.now()
            // Время за сегодня до этого момента (уже включает только что начатую сессию)
            var usageMs = container.usageRepository.getTodayUsage()[pkg] ?: 0L
            var lastTick = SystemClock.elapsedRealtime()

            while (isActive) {
                val onScreen = powerManager.isInteractive && isOnScreen(pkg)

                // Режим фокуса главнее всех правил и пропусков: блокируем сразу
                val session = focus
                if (session != null && session.isActive() && pkg in session.apps) {
                    if (onScreen) {
                        val until = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.endsAt), ZoneId.systemDefault())
                        scope.launch { block(pkg, BlockDecision(BlockReason.FocusMode(until), until), bankMultiplier = null) }
                        return@launch
                    }
                    delay(1_000)
                    continue
                }

                val rule = rules[pkg] ?: return@launch // фокус закончился, а правила нет — проверять нечего

                // Добавляем прошедшее время, только если приложение реально на включённом экране
                val nowTick = SystemClock.elapsedRealtime()
                val elapsed = nowTick - lastTick
                if (onScreen) usageMs += elapsed
                lastTick = nowTick
                // Части приложения (Shorts): закрыть или посчитать время, если у них лимит
                if (onScreen) checkInsideApp(pkg, elapsed)

                // Наступила полночь — счётчик за день начинается заново
                if (LocalDate.now() != day) {
                    day = LocalDate.now()
                    usageMs = 0L
                }

                val now = LocalDateTime.now()
                val decisions = RuleEvaluator.evaluateAll(rule, usageMs, now)
                val decision = RuleEvaluator.pick(decisions)
                // Цена минуты из банка при нынешних нарушениях (null — банк здесь не работает)
                val multiplier = BankPricing.multiplier(decisions.map { it.reason }, insideBlockedBuyable)

                // Действует разовый пропуск — время считаем, но не блокируем и не предупреждаем.
                // На полную блокировку пропуски не действуют.
                if (decision?.reason != BlockReason.FullBlock && hasActivePass(pkg)) {
                    delay(1_000)
                    continue
                }

                // Идёт время из банка: пускаем, если оно покрывает нынешние нарушения
                val bankSession = activeBankSession(pkg)
                if (bankSession != null) {
                    if (decision == null || (multiplier != null && multiplier <= bankSession.multiplier)) {
                        delay(1_000)
                        continue
                    }
                    // Например, началось запрещённое окно: сессия заканчивается, остаток возвращается
                    container.bankRepository.endSession(pkg)
                }

                if (decision != null && onScreen) {
                    // Блокируем в отдельной задаче: переход на домашний экран отменит эту проверку,
                    // а показ экрана блокировки отменяться не должен
                    scope.launch { block(pkg, decision, multiplier) }
                    return@launch
                }
                if (onScreen) {
                    val warnings = RuleEvaluator.upcomingWarnings(rule, usageMs, now, warningMinutes * 60_000L)
                    warnings.forEach { showWarningOnce(pkg, it, day) }
                }
                delay(1_000)
            }
        }
    }

    /** Идущее сейчас время из банка для приложения [pkg] (или null). */
    private fun activeBankSession(pkg: String): BankSession? {
        val now = System.currentTimeMillis()
        val all = bankSessions + listOfNotNull(container.bankRepository.lastStarted)
        return all.firstOrNull { it.packageName == pkg && now >= it.startedAt && now < it.endsAt }
    }

    /**
     * Ушли из приложения [pkg] (или погас экран). Если шло время из банка — через несколько секунд
     * сессия закончится и неиспользованные минуты вернутся. Несколько секунд — чтобы не обрывать
     * сессию из-за мелочей вроде системного окна выбора аккаунта.
     */
    private fun leaveApp(pkg: String?) {
        if (pkg == null || activeBankSession(pkg) == null) return
        val leftAt = System.currentTimeMillis()
        sessionEndJobs.remove(pkg)?.cancel()
        sessionEndJobs[pkg] = scope.launch {
            delay(SESSION_END_DELAY_MS)
            sessionEndJobs.remove(pkg)
            container.bankRepository.endSession(pkg, leftAt)
        }
    }

    /** Вернулись в приложение [pkg] быстро — сессия банка продолжается. */
    private fun returnToApp(pkg: String) {
        sessionEndJobs.remove(pkg)?.cancel()
    }

    private fun hasActivePass(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val all = passes + listOfNotNull(container.passRepository.lastGranted)
        return all.any { it.packageName == pkg && now >= it.startedAt && now < it.endsAt }
    }

    /** Показать предупреждение, если такое ещё не показывали. */
    private suspend fun showWarningOnce(pkg: String, warning: BlockWarning, today: LocalDate) {
        val key = when (warning) {
            is BlockWarning.LimitEndsSoon -> "limit:$pkg:$today"
            is BlockWarning.AllowedPeriodEnds -> "allow:$pkg:${warning.at}"
            is BlockWarning.BlockedPeriodStarts -> "block:$pkg:${warning.at}"
        }
        if (!shownWarnings.add(key)) return

        val label = container.appsRepository.getApp(pkg)?.label ?: pkg
        val strings = Notifications.localized(this)
        val (title, text) = when (warning) {
            is BlockWarning.LimitEndsSoon -> {
                // Округляем вверх: 2 мин 10 с — это "осталось 3 мин"
                val minutesLeft = (warning.remainingMs + 59_999) / 60_000
                strings.getString(R.string.warning_limit_title, label, formatDuration(strings, minutesLeft * 60_000)) to
                    strings.getString(R.string.warning_limit_text)
            }
            is BlockWarning.AllowedPeriodEnds ->
                strings.getString(R.string.warning_allowed_title, label, formatTimeOfDay(this, warning.at)) to
                    strings.getString(R.string.warning_allowed_text)
            is BlockWarning.BlockedPeriodStarts ->
                strings.getString(R.string.warning_blocked_title, label, formatTimeOfDay(this, warning.at)) to
                    strings.getString(R.string.warning_blocked_text)
        }
        Notifications.showWarning(this, key.hashCode(), title, text)
    }

    /** Увести с заблокированного приложения и показать экран блокировки. */
    private suspend fun block(pkg: String, decision: BlockDecision, bankMultiplier: Int?) {
        Log.i(TAG, "Блокировка $pkg: ${decision.reason}")
        currentPackage = null // при следующем открытии приложение проверится заново
        performGlobalAction(GLOBAL_ACTION_HOME)
        container.currentBlock.value = BlockInfo(pkg, decision, bankMultiplier)
        delay(300) // даём домашнему экрану открыться, чтобы экран блокировки оказался поверх него
        startActivity(
            Intent(this, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        container.blockLogRepository.log(pkg, decision.reason) // для статистики
    }

    /** Приложение [pkg] сейчас в активном окне? (Пока нет данных — считаем, что да.) */
    private fun isOnScreen(pkg: String): Boolean {
        val active = rootInActiveWindow?.packageName?.toString() ?: return true
        return active == pkg
    }

    /** Окно — это экран приложения (Activity), а не диалог или клавиатура. */
    private fun isActivity(pkg: String, cls: String): Boolean {
        val component = ComponentName(pkg, cls)
        return activityCache.getOrPut(component) {
            try {
                packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun findLauncherPackage(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(
            homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )?.activityInfo?.packageName
    }

    private companion object {
        const val TAG = "TakeYourBrain"

        /** Части приложений: как часто проверять экран и сколько не трогать после "Назад". */
        const val INSIDE_CHECK_MS = 300L
        const val ELEMENT_COOLDOWN_MS = 1_500L
        const val ELEMENT_SAVE_MS = 15_000L

        /** Отладочный дамп — не чаще раза в 3 секунды. */
        const val DUMP_INTERVAL_MS = 3_000L

        /** Через сколько после ухода из приложения заканчивается время из банка (остаток возвращается). */
        const val SESSION_END_DELAY_MS = 10_000L
    }
}
