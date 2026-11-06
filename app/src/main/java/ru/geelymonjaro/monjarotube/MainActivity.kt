package ru.geelymonjaro.monjarotube

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import org.json.JSONTokener
import ru.geelymonjaro.monjarotube.proxy.ByeDpiService
import ru.geelymonjaro.monjarotube.proxy.DpiProfiles
import java.util.concurrent.Executor
import java.net.InetAddress

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var ui: TubeUi
    private var selectedMode = 1
    private var selectedProfile = 9
    private var selectedAccountVariant = 0
    private var currentUrl = "https://m.youtube.com/"
    private var menuTab = 0
    private var updateClient: GitHubUpdates? = null
    private var updateDialog: AlertDialog? = null
    private var browser: WebView? = null
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executor { runOnUiThread(it) }
    private var connection: ServiceConnection? = null
    private var remote: Messenger? = null
    private var generation = 0
    private var activeMode = -1
    private var runningMode = -1
    private var routeReady = false
    private var proxySupported = false
    private var engineStats = longArrayOf(0, 0, 0, 0)
    private val events = ArrayDeque<String>()
    private var videoScriptInstalled = false
    private var mainPageFailed = false
    private var authPageFailed = false
    private var pageStartedAt = 0L
    private var lastNativeError = ""
    private var lastNetworkReport = "Проверка сети ещё не запускалась"
    private var runningProfile = 9
    private var runningCustom = ""
    private var runningAccountVariant = 0
    private val preferences by lazy { getSharedPreferences("dpi", MODE_PRIVATE) }
    private val statsPoll = object : Runnable {
        override fun run() {
            try { remote?.send(Message.obtain(null, ByeDpiService.STATS).apply { replyTo = receiver }) }
            catch (_: RemoteException) { failRoute("Соединение с ByeDPI потеряно") }
            handler.postDelayed(this, 1500)
        }
    }
    private var receiver: Messenger? = null
    private fun newReceiver(token: Int) = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (token != generation) return@Handler true
        when (message.what) {
            ByeDpiService.READY -> if (runningMode == 1 && connection != null) applyWebProxy("socks://127.0.0.1:1088", generation)
            ByeDpiService.FAILED -> failRoute("ByeDPI не запущен: проверьте журнал и занятость порта 1088")
            ByeDpiService.STATS -> {
                engineStats = message.data.getLongArray("stats") ?: engineStats
                lastNativeError = message.data.getString("lastError").orEmpty()
                if (engineStats[0] == -1L && runningMode == 1) failRoute("ByeDPI завершился; загрузка заблокирована")
            }
        }
        true
    })
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateKeepScreenOn()
        // The first polished release starts with embedded bypass. Later choices are remembered.
        if (!preferences.getBoolean("automaticLaunch", false)) {
            preferences.edit().putBoolean("automaticLaunch", true).putInt("mode", 1).apply()
        }
        selectedMode = preferences.getInt("mode", 1).coerceIn(0, 2)
        selectedProfile = preferences.getInt("profile", 9)
        if (preferences.getInt("profileSchema", 0) == 0 && preferences.contains("profile") && selectedProfile == 9) selectedProfile = 10
        selectedProfile = selectedProfile.coerceIn(DpiProfiles.names.indices)
        selectedAccountVariant = preferences.getInt("accountVariant", 0).coerceIn(DpiProfiles.accountNames.indices)
        preferences.edit().putInt("profileSchema", 1).putInt("profile", selectedProfile).apply()
        val savedUrl = savedInstanceState?.getString("page") ?: if (preferences.getBoolean("lastPage", true)) preferences.getString("lastPageUrl", null) else null
        if (NavigationPolicy.isContentPage(savedUrl.orEmpty())) currentUrl = savedUrl!!
        ui = TubeUi(this)
        root = ui.root; content = ui.content
        ui.buildToolbar(
            search = { showSearch() },
            home = { navigate("https://m.youtube.com/") },
            reload = { reloadPage() },
            settings = { showSettings() })
        setContentView(root)
        window.statusBarColor = ui.bg; window.navigationBarColor = ui.bg
        window.decorView.systemUiVisibility = 0
        try {
            val provider = WebView.getCurrentWebViewPackage()
            if (provider == null) { ui.error("На устройстве отсутствует системный WebView", { switchRoute() }); return }
            proxySupported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
            event("WebView: ${provider.packageName} ${provider.versionName}; PROXY_OVERRIDE=$proxySupported")
        } catch (e: Throwable) { ui.error("WebView недоступен: ${e.javaClass.simpleName}", { switchRoute() }); return }
        handler.post(statsPoll)
        switchRoute()
    }
    private fun dp(value: Int) = ui.dp(value)
    private fun updateKeepScreenOn() {
        if (preferences.getBoolean("keepScreenOn", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun showSettings(tab: Int = menuTab) {
        menuTab = tab
        ui.settings(tab) { currentTab, body ->
            menuTab = currentTab
            when (currentTab) {
                0 -> {
                    ui.section(body, "Соединение", "Выбранный режим запускается вместе с приложением.")
                    ui.choice(body, "Встроенный обход", "YouTube через ByeDPI и защищённый DNS", selectedMode == 1, true) { changeMode(1) }
                    ui.choice(body, "Без обхода", "Обычное соединение вашего устройства", selectedMode == 0) { changeMode(0) }
                    ui.choice(body, "Внешний прокси", "Для уже настроенного SOCKS-сервера", selectedMode == 2) { changeMode(2) }
                    if (selectedMode == 1) {
                        ui.section(body, "Профиль обхода")
                        val card = ui.card(body)
                        ui.item(card, "settings", DpiProfiles.names[selectedProfile], "Нажмите, чтобы выбрать другой профиль") { chooseProfile() }
                        if (selectedProfile == DpiProfiles.YOUTUBE_PROFILE) {
                            ui.divider(card)
                            ui.item(card, "account", "Вариант входа: ${DpiProfiles.accountNames[selectedAccountVariant]}", "При таймауте после подтверждения Google") { chooseAccountVariant() }
                        }
                        ui.divider(card)
                        ui.item(card, "code", "Свои параметры", "Расширенная настройка ByeDPI") { editParameters() }
                    }
                    if (selectedMode == 2) ui.note(body, "SOCKS5 должен работать на этом устройстве: 127.0.0.1:1080. Отдельный сервер запускается самостоятельно.")
                    ui.note(body, "Смена соединения перезагрузит открытую страницу.")
                    val tools = ui.card(body)
                    ui.item(tools, "network", "Проверить соединение", "YouTube и оба сервера входа") { checkNetwork() }
                }
                1 -> {
                    ui.section(body, "Просмотр", "Настройте привычный запуск YouTube.")
                    val card = ui.card(body)
                    ui.toggle(card, "Последняя страница", "Открывать её при следующем запуске", preferences.getBoolean("lastPage", true)) {
                        preferences.edit().putBoolean("lastPage", it).apply()
                    }
                    ui.divider(card)
                    ui.toggle(card, "Не выключать экран", "Пока приложение открыто", preferences.getBoolean("keepScreenOn", true)) {
                        preferences.edit().putBoolean("keepScreenOn", it).apply(); updateKeepScreenOn()
                    }
                    ui.section(body, "Навигация")
                    val links = ui.card(body)
                    ui.item(links, "search", "Поиск YouTube", "Ввод через клавиатуру Android") { ui.closeSettings(); showSearch() }
                    ui.divider(links)
                    ui.item(links, "home", "Главная YouTube", "Лента и поиск видео") { ui.closeSettings(); navigate("https://m.youtube.com/") }
                    ui.divider(links)
                    ui.item(links, "link", "Открыть ссылку", "Видео, канал или плейлист YouTube") { openLinkDialog() }
                    ui.divider(links)
                    ui.item(links, "reload", "Обновить страницу", "Повторить загрузку") { ui.closeSettings(); reloadPage() }
                }
                else -> {
                    ui.section(body, "MonjaroTube ${BuildConfig.VERSION_NAME}")
                    val updates = ui.card(body)
                    ui.item(updates, "reload", "Проверить обновления", "Новая версия из GitHub Releases") { checkUpdates() }
                    ui.divider(updates)
                    ui.item(updates, "link", "Проект на GitHub", "Исходники, выпуски и обратная связь") { openProjectUrl(UpdatePolicy.REPOSITORY_URL) }
                    ui.section(body, "Аккаунт YouTube")
                    val account = ui.card(body)
                    ui.item(account, "account", "Вход в аккаунт", "Официальная страница Google") { ui.closeSettings(); retrySignIn() }
                    ui.note(body, "Вход и подтверждение выполняются на странице Google. Если Google ограничит встроенный браузер, доступна справка о входе.")
                    val help = ui.card(body)
                    ui.item(help, "info", "Помощь со входом", "Ограничения Google и внешний браузер") { accountHelp() }
                    ui.section(body, "Поддержка")
                    val tools = ui.card(body)
                    ui.item(tools, "code", "Диагностика", "Состояние видео, обхода и журнал") { diagnostics() }
                    ui.divider(tools)
                    ui.item(tools, "info", "О приложении", "Версия, компоненты и лицензии") { showAbout() }
                    ui.note(body, "Проверяйте настройки и смотрите видео только на стоянке.")
                }
            }
        }
    }
    private fun changeMode(value: Int) {
        if (selectedMode == value && routeReady) return
        selectedMode = value
        switchRoute(); showSettings(0)
    }
    private fun chooseProfile() {
        val dialog = AlertDialog.Builder(this).setTitle("Профиль обхода")
            .setSingleChoiceItems(DpiProfiles.names.toTypedArray(), selectedProfile) { choice, index ->
                choice.dismiss()
                if (index == DpiProfiles.names.lastIndex) editParameters()
                else { selectedProfile = index; switchRoute(); showSettings(0) }
            }.setNegativeButton("Отмена", null).create()
        dialog.show()
    }
    private fun chooseAccountVariant() {
        AlertDialog.Builder(this).setTitle("Вариант завершения входа")
            .setSingleChoiceItems(DpiProfiles.accountNames.toTypedArray(), selectedAccountVariant) { dialog, index ->
                dialog.dismiss()
                if (selectedAccountVariant != index) {
                    selectedAccountVariant = index
                    switchRoute()
                    showSettings(0)
                }
            }.setNegativeButton("Отмена", null).show()
        Toast.makeText(this, "Меняйте вариант до начала входа. Соединение перезапустится.", Toast.LENGTH_LONG).show()
    }
    private fun reloadPage() {
        if (authPageFailed) retrySignIn()
        else if (routeReady) browser?.reload()
        else switchRoute()
    }
    private fun retrySignIn() {
        // Restart the official flow; never replay a failed one-time SetSID URL or a form POST.
        currentUrl = NavigationPolicy.SIGN_IN
        val view = browser
        if (!routeReady || view == null) { switchRoute(false); return }
        view.stopLoading()
        CookieManager.getInstance().flush()
        ui.connecting({ retrySignIn() }, "Открываем вход Google", "Сохранённая сессия Google будет использована при возможности")
        view.loadUrl(NavigationPolicy.SIGN_IN)
    }
    private fun openLinkDialog() {
        val editor = EditText(this).apply {
            hint = "https://m.youtube.com/watch?v=…"; textSize = 15f
            setSingleLine(true); setSelectAllOnFocus(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Открыть ссылку YouTube").setView(editor)
            .setNegativeButton("Отмена", null).setPositiveButton("Открыть", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = editor.text.toString().trim()
                if (!NavigationPolicy.isYoutube(value)) editor.error = "Нужна HTTPS-ссылка YouTube"
                else { dialog.dismiss(); ui.closeSettings(); navigate(value) }
            }
        }
        dialog.show()
    }
    private fun showSearch() {
        ui.closeSettings()
        val editor = EditText(this).apply {
            hint = "Что найти на YouTube?"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Поиск YouTube").setView(editor)
            .setNegativeButton("Отмена", null).setPositiveButton("Найти", null).create()
        fun submit() {
            val query = editor.text.toString().trim()
            if (query.isEmpty()) { editor.error = "Введите запрос"; return }
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken, 0)
            dialog.dismiss()
            navigate(SearchNavigation.url(query))
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            editor.requestFocus()
            editor.viewTreeObserver.addOnWindowFocusChangeListener(object : android.view.ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (hasFocus) {
                        editor.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                        editor.post { if (editor.isAttachedToWindow) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, 0) }
                    }
                }
            })
            editor.post { if (editor.hasWindowFocus()) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, 0) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
        }
        editor.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEARCH) { submit(); true } else false }
        dialog.show()
    }
    private fun navigate(url: String) {
        if (!NavigationPolicy.allows(url)) return
        currentUrl = url
        if (routeReady) browser?.loadUrl(url) else switchRoute(false)
    }
    private fun accountHelp(externalUrl: String = "https://m.youtube.com/") {
        AlertDialog.Builder(this).setTitle("Вход в YouTube")
            .setMessage("Google может ограничивать вход во встроенном браузере. Откройте YouTube во внешнем браузере, если появится сообщение об ограничении. Вход в браузере сам по себе не переносит аккаунт в MonjaroTube. Встроенный обход действует только внутри приложения.")
            .setPositiveButton("Открыть браузер") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(this, "На устройстве нет подходящего браузера", Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Закрыть", null).show()
    }
    private fun showAbout() {
        showReport("MonjaroTube ${BuildConfig.VERSION_NAME}\n\nYouTube в системном WebView. Android 11 и выше.\n\nСобственный код: MIT.\nByeDPI: hufrea/byedpi, MIT.\nkavl: Attractive Chaos, MIT.\nAndroidX: Apache 2.0.\n\nПриложение не связано с Google, YouTube или Geely.\nТексты лицензий включены в APK: assets/licenses.\n\n${UpdatePolicy.REPOSITORY_URL}", "О приложении")
    }
    private fun openProjectUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: ActivityNotFoundException) {
            AlertDialog.Builder(this).setTitle("Откройте ссылку на другом устройстве")
                .setMessage(url).setPositiveButton("Скопировать") { _, _ ->
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("MonjaroTube GitHub", url))
                }.setNegativeButton("Закрыть", null).show()
        }
    }
    private fun cancelUpdateCheck() {
        val client = updateClient
        updateClient = null
        client?.cancel()
        updateDialog?.dismiss(); updateDialog = null
    }
    private fun checkUpdates() {
        if (updateClient != null) return
        ui.closeSettings()
        val client = GitHubUpdates()
        updateClient = client
        updateDialog = AlertDialog.Builder(this).setTitle("Проверка обновлений")
            .setMessage("Ищем стабильную версию на GitHub…")
            .setNegativeButton("Отмена") { _, _ -> cancelUpdateCheck() }
            .setOnCancelListener { cancelUpdateCheck() }.show()
        Thread({
            val result = runCatching { client.latest() }
            runOnUiThread {
                if (isDestroyed || updateClient !== client) return@runOnUiThread
                updateClient = null
                updateDialog?.dismiss()
                val dialog = AlertDialog.Builder(this)
                result.fold(onSuccess = { release ->
                    val installed = requireNotNull(UpdatePolicy.version(BuildConfig.VERSION_NAME))
                    if (release.version > installed) {
                        dialog.setTitle("Доступна версия ${release.version}")
                            .setMessage("Установлена версия ${BuildConfig.VERSION_NAME}." +
                                (if (release.notes.isBlank()) "" else "\n\n${release.notes}") +
                                "\n\nAPK откроется в браузере. Установите его поверх приложения, без удаления: это сохранит вход и настройки.")
                            .setPositiveButton("Скачать APK") { _, _ -> openProjectUrl(release.downloadUrl) }
                            .setNeutralButton("О выпуске") { _, _ -> openProjectUrl(UpdatePolicy.releaseUrl(release.version)) }
                    } else {
                        dialog.setTitle("Обновление не требуется")
                            .setMessage("Установлена версия ${BuildConfig.VERSION_NAME}. Более новой стабильной версии на GitHub нет.")
                    }
                }, onFailure = { error ->
                    dialog.setTitle("Не удалось проверить обновления").setMessage(when (error) {
                        is GitHubUpdates.NoRelease -> "GitHub пока не вернул стабильный выпуск. Попробуйте позже или откройте страницу проекта."
                        is GitHubUpdates.RateLimited -> "GitHub временно ограничил число запросов. Попробуйте позже или откройте страницу проекта."
                        else -> "Проверьте доступ к Интернету и повторите позже. Для проверки обновлений нужен доступ к GitHub; просмотр YouTube продолжает работать независимо."
                    }).setPositiveButton("Открыть GitHub") { _, _ -> openProjectUrl(UpdatePolicy.REPOSITORY_URL) }
                })
                updateDialog = dialog.setNegativeButton("Закрыть", null).show()
            }
        }, "MonjaroTube-updates").start()
    }
    private fun event(text: String) {
        if (events.size >= 60) events.removeFirst()
        events.addLast(text)
        android.util.Log.i("MonjaroTube", text)
    }
    private fun destroyBrowser() {
        hideFullscreen()
        browser?.apply { settings.blockNetworkLoads = true; stopLoading(); content.removeView(this); removeAllViews(); destroy() }
        browser = null
        videoScriptInstalled = false
    }
    private fun disconnect() {
        remote = null
        receiver = null
        connection?.let { try { unbindService(it) } catch (_: IllegalArgumentException) {} }
        connection = null
    }
    private fun failRoute(reason: String) {
        routeReady = false
        generation++
        destroyBrowser()
        disconnect()
        runningMode = -1
        activeMode = -1
        ui.error(reason, { switchRoute() })
        event(reason)
    }
    private fun switchRoute(preservePage: Boolean = true) {
        if (preservePage) browser?.url?.takeIf { NavigationPolicy.allows(it) }?.let {
            currentUrl = if (NavigationPolicy.authenticationHost(it) != null) NavigationPolicy.SIGN_IN else it
        }
        val selected = selectedMode
        val custom = preferences.getString("custom", "-s 1+s").orEmpty()
        try { if (selected == 1) DpiProfiles.arguments(selectedProfile, custom, selectedAccountVariant) }
        catch (e: IllegalArgumentException) { showReport(e.message.orEmpty()); return }
        if (selected != 0 && !proxySupported) { failRoute("Этот WebView не поддерживает прокси. Нужна другая версия WebView."); return }
        generation++
        val token = generation
        routeReady = false
        destroyBrowser()
        disconnect()
        runningMode = selected
        runningProfile = selectedProfile
        runningCustom = custom
        runningAccountVariant = selectedAccountVariant
        preferences.edit().putInt("mode", selected).putInt("profile", runningProfile).putInt("accountVariant", selectedAccountVariant).apply()
        activeMode = -1
        engineStats = longArrayOf(0, 0, 0, 0)
        lastNativeError = ""
        lastNetworkReport = "Проверка сети ещё не запускалась для этого режима"
        ui.connecting({ switchRoute() })
        if (selected == 1) {
            // Let the previous private service process finish before rebinding its fixed loopback port.
            handler.postDelayed({ if (token == generation) bindEngine(token) }, 400)
        } else if (selected == 2) {
            Thread({
                val check = Socks5Probe.check(1080)
                runOnUiThread {
                    if (token == generation) {
                        if (check.ok) applyWebProxy("socks://127.0.0.1:1080", token)
                        else {
                            event(check.message)
                            failRoute("Внешний прокси недоступен. Запустите SOCKS5-сервер на этом устройстве (127.0.0.1:1080) или выберите встроенный обход в настройках.")
                        }
                    }
                }
            }, "ExternalSocksCheck").start()
        } else {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try { ProxyController.getInstance().clearProxyOverride(executor) { routeApplied(token, selected) } }
                catch (e: Exception) { failRoute("Не удалось сбросить прокси: ${e.javaClass.simpleName}") }
            } else routeApplied(token, selected)
        }
    }
    private fun bindEngine(token: Int) {
        receiver = newReceiver(token)
        engineStats = longArrayOf(0, 0, 0, 0)
        handler.postDelayed({ if (token == generation && !routeReady) failRoute("Время запуска ByeDPI истекло. Повторите подключение в настройках.") }, 12000)
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (token != generation) return
                remote = Messenger(binder)
                try { remote?.send(Message.obtain(null, ByeDpiService.START, runningProfile, 0).apply {
                    replyTo = receiver
                    data.putString("custom", runningCustom)
                    data.putInt("accountVariant", runningAccountVariant)
                }) }
                catch (_: RemoteException) { failRoute("Не удалось запустить ByeDPI") }
            }
            override fun onServiceDisconnected(name: ComponentName) { if (token == generation) failRoute("ByeDPI остановлен; сетевые загрузки заблокированы") }
            override fun onBindingDied(name: ComponentName) { if (token == generation) failRoute("Служба ByeDPI недоступна") }
            override fun onNullBinding(name: ComponentName) { if (token == generation) failRoute("Служба ByeDPI не подключилась") }
        }
        connection = serviceConnection
        if (!bindService(Intent(this, ByeDpiService::class.java), serviceConnection, BIND_AUTO_CREATE)) failRoute("Не удалось подключить ByeDPI")
    }
    private fun applyWebProxy(endpoint: String, token: Int) {
        if (token != generation) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) { failRoute("WebView не поддерживает прокси"); return }
        try {
            // No addDirect(): an unavailable proxy must not silently turn into a direct connection.
            val config = ProxyConfig.Builder().addProxyRule(endpoint).build()
            ProxyController.getInstance().setProxyOverride(config, executor) { routeApplied(token, runningMode) }
        } catch (e: Exception) { failRoute("Прокси не применён: ${e.javaClass.simpleName}") }
    }
    private fun routeApplied(token: Int, selected: Int) {
        if (token != generation || isFinishing || isDestroyed) return
        activeMode = selected
        try {
            createBrowser()
            routeReady = true
            ui.connected(selected)
            event("Настройка WebView применена: ${modeName(activeMode)}; доступ к YouTube пока не проверен")
            browser?.loadUrl(currentUrl)
        } catch (e: Throwable) { failRoute("Не удалось создать WebView: ${e.javaClass.simpleName}") }
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser() {
        val view = KeyboardWebView(this)
        browser = view
        view.settings.apply {
            blockNetworkLoads = true
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(true)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(view, true) }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                if (NavigationPolicy.isGoogleOAuth(request.url.toString())) {
                    accountHelp(request.url.toString()); return true
                }
                if (NavigationPolicy.allows(request.url.toString())) return false
                event("Внешний переход заблокирован: ${request.url.scheme}://${request.url.host.orEmpty()}")
                return true
            }
            override fun onPageStarted(v: WebView, url: String?, icon: android.graphics.Bitmap?) {
                videoScriptInstalled = false
                mainPageFailed = false
                authPageFailed = false
                pageStartedAt = SystemClock.elapsedRealtime()
                val host = NavigationPolicy.authenticationHost(url.orEmpty())
                ui.accountPage(host)
                if (host != null) event("Переход входа: $host")
            }
            override fun onPageCommitVisible(v: WebView, url: String?) { if (!mainPageFailed) v.visibility = View.VISIBLE }
            override fun onPageFinished(v: WebView, url: String?) {
                if (!mainPageFailed && NavigationPolicy.allows(url.orEmpty())) {
                    currentUrl = url.orEmpty()
                    if (NavigationPolicy.isContentPage(currentUrl)) {
                        preferences.edit().putString("lastPageUrl", currentUrl).apply()
                        installMediaProbe(v)
                    }
                    CookieManager.getInstance().flush()
                    v.visibility = View.VISIBLE
                    ui.connected(activeMode)
                    val host = Uri.parse(url).host
                    event("Страница готова: $host (${SystemClock.elapsedRealtime() - pageStartedAt} мс)")
                }
            }
            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    mainPageFailed = true
                    // Chromium's default error page exposes full auth URLs, so keep it hidden.
                    v.visibility = View.INVISIBLE
                    authPageFailed = NavigationPolicy.authenticationHost(request.url.toString()) != null
                    val elapsed = SystemClock.elapsedRealtime() - pageStartedAt
                    val details = "Ошибка WebView ${error.errorCode} (host=${request.url.host}; ${elapsed} мс)"
                    if (authPageFailed) {
                        currentUrl = NavigationPolicy.SIGN_IN
                        CookieManager.getInstance().flush()
                        ui.error("Не удалось связаться с ${request.url.host}. Данные браузера не очищены. Повторите вход; если ошибка повторится, проверьте серверы входа и выберите другой вариант в разделе «Соединение».",
                            { retrySignIn() }, "Не удалось завершить вход", "Повторить вход")
                    } else ui.error("Страница не загрузилась. Повторите подключение или выберите другой режим в настройках.", { switchRoute() })
                    event(details)
                }
            }
            override fun onReceivedHttpError(v: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) { mainPageFailed = true; event("HTTP ${response.statusCode}: ${request.url.host}") }
            }
            override fun onReceivedSslError(v: WebView, ssl: SslErrorHandler, error: SslError) {
                ssl.cancel(); event("Ошибка TLS: соединение отменено")
                ui.error("Не удалось проверить защищённое соединение с сайтом. Проверьте дату устройства и подключение.", { switchRoute() })
            }
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                content.removeView(v)
                v.destroy()
                browser = null
                routeReady = false
                ui.error("Процесс WebView завершился. Повторите подключение.", { switchRoute() })
                event("WebView renderer gone; crash=${detail.didCrash()}")
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { ui.progress(newProgress) }
            override fun onShowCustomView(customView: View, callback: CustomViewCallback) {
                if (fullscreen != null) { callback.onCustomViewHidden(); return }
                fullscreen = customView; fullscreenCallback = callback
                root.visibility = View.GONE
                addContentView(customView, ViewGroup.LayoutParams(-1, -1))
                window.insetsController?.apply {
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsets.Type.systemBars())
                }
            }
            override fun onHideCustomView() { hideFullscreen() }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.settings.blockNetworkLoads = false
    }
    private fun hideFullscreen() {
        fullscreen?.let { (it.parent as? ViewGroup)?.removeView(it) }
        fullscreen = null
        fullscreenCallback?.onCustomViewHidden(); fullscreenCallback = null
        if (::root.isInitialized) root.visibility = View.VISIBLE
        window.insetsController?.show(WindowInsets.Type.systemBars())
    }
    private fun installMediaProbe(view: WebView) {
        if (videoScriptInstalled) return
        videoScriptInstalled = true
        // Local DOM inspection only; no JavaScript interface exposed to web pages.
        view.evaluateJavascript("""
            (()=>{if(window.__mtProbe)return;window.__mtProbe={events:[]};
            const track=v=>{if(v.dataset.mtProbe)return;v.dataset.mtProbe='1';
            ['playing','waiting','stalled','error','pause','ended'].forEach(e=>v.addEventListener(e,()=>{
            let a=window.__mtProbe.events;a.push(e+' @ '+v.currentTime.toFixed(1));if(a.length>12)a.shift();}));};
            new MutationObserver(()=>document.querySelectorAll('video').forEach(track)).observe(document,{childList:true,subtree:true});
            document.querySelectorAll('video').forEach(track);})()
        """.trimIndent(), null)
    }
    private fun diagnostics() {
        val prefix = buildString {
            append("MonjaroTube ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n")
            append("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}\nABI: ${Build.SUPPORTED_ABIS.joinToString()}\n")
            val provider = try { WebView.getCurrentWebViewPackage() } catch (_: Throwable) { null }
            append("WebView: ${provider?.packageName} ${provider?.versionName}\nPROXY_OVERRIDE: $proxySupported\n")
            append("Активный режим: ${modeName(activeMode)}\nНастройка WebView применена: $routeReady (это не проверка доступности Интернета)\n")
            append("Google Play Services: ${hasPlayServices()} (не используется приложением)\n")
            if (activeMode == 1) {
                if (runningProfile == DpiProfiles.YOUTUBE_PROFILE) append("Вариант входа: ${DpiProfiles.accountNames[runningAccountVariant]}\n")
                append("Параметры ByeDPI: ${DpiProfiles.arguments(runningProfile, runningCustom, runningAccountVariant).joinToString(" ").ifBlank { "без обхода" }}\n")
                append("ByeDPI: SOCKS DNS-запросов ${engineStats[1]}, googlevideo ${engineStats[2]}, байт TCP ${engineStats[3]}\n")
                append("Данные текущего процесса ByeDPI обновляются каждые 1,5 с.\n")
                append("DNS ядра: встроенный DoH Cloudflare (IPv4), затем системный DNS. Настройки планшета не меняются.\n")
                if (lastNativeError.isNotBlank()) append("Последняя ошибка ядра: $lastNativeError\n")
            } else append("Счётчики встроенного ByeDPI не применяются к этому режиму.\n")
            append("\nПроверка сети:\n$lastNetworkReport\n")
        }
        val view = browser
        if (view == null) { showReport(prefix + "\nWebView ещё не запущен.\n" + events.joinToString("\n")); return }
        view.evaluateJavascript("""
            JSON.stringify({video:[...document.querySelectorAll('video')].map(v=>({paused:v.paused,time:v.currentTime,
            duration:Number.isFinite(v.duration)?v.duration:null,readyState:v.readyState,networkState:v.networkState,
            width:v.videoWidth,height:v.videoHeight,error:v.error?{code:v.error.code,message:v.error.message}:null,
            buffered:[...Array(v.buffered.length)].map((_,i)=>[v.buffered.start(i),v.buffered.end(i)])})),events:window.__mtProbe?.events||[]})
        """.trimIndent()) { raw ->
            val result = try { JSONTokener(raw).nextValue().toString() } catch (_: Exception) { raw }
            showReport(prefix + "\nВидео DOM:\n" + result + "\n\nЖурнал:\n" + events.joinToString("\n"))
        }
    }
    private fun modeName(value: Int) = when (value) {
        0 -> "обычное соединение"
        1 -> "встроенный ByeDPI, 127.0.0.1:1088"
        2 -> "внешний SOCKS, 127.0.0.1:1080 (отдельный сервер)"
        else -> "не применён"
    }
    private fun checkNetwork() {
        val token = generation
        val selected = activeMode
        if (!routeReady) { showReport("Подключение ещё не готово. Повторите его в разделе «Соединение»."); return }
        Toast.makeText(this, "Проверяем соединение…", Toast.LENGTH_SHORT).show()
        Thread({
            val lines = mutableListOf("Режим: ${modeName(selected)}")
            try {
                val addresses = InetAddress.getAllByName("m.youtube.com").map { it.hostAddress }.distinct()
                lines += "Системный DNS m.youtube.com: ${addresses.joinToString()}"
            } catch (e: Exception) { lines += "Системный DNS m.youtube.com: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(200)}" }
            if (selected == 1 || selected == 2) {
                val port = if (selected == 1) 1088 else 1080
                for (host in listOf("m.youtube.com", "accounts.google.com", "accounts.youtube.com")) {
                    val started = SystemClock.elapsedRealtime()
                    val result = Socks5Probe.check(port, host, https = true)
                    lines += "$host: ${if (result.ok) "HTTPS отвечает" else "сбой"}; ${SystemClock.elapsedRealtime() - started} мс\n${result.message}"
                }
                lines += "Это запросы к корню сайтов без данных аккаунта. HTTP 404 у accounts.youtube.com подтверждает транспорт, но не завершение входа. WebView может вести себя иначе, чем эта проверка."
            } else lines += "SOCKS не проверялся: сначала примените встроенный или внешний режим."
            val report = lines.joinToString("\n")
            runOnUiThread {
                if (token == generation && !isFinishing && !isDestroyed) {
                    lastNetworkReport = report
                    event(report)
                    showReport(report)
                }
            }
        }, "NetworkDiagnostics").start()
    }
    private fun editParameters() {
        val editor = EditText(this).apply {
            setText(preferences.getString("custom", "-s 1+s"))
            hint = "Например: -s 1+s -r 3+s"
            setSingleLine(false)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Свои параметры ByeDPI")
            .setMessage("Параметры обхода через пробел. После сохранения соединение перезапустится.")
            .setView(editor).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    DpiProfiles.parse(editor.text.toString())
                    preferences.edit().putString("custom", editor.text.toString()).apply()
                    selectedProfile = DpiProfiles.names.lastIndex
                    dialog.dismiss()
                    switchRoute(); showSettings(0)
                } catch (e: IllegalArgumentException) { editor.error = e.message }
            }
        }
        dialog.show()
    }
    private fun hasPlayServices(): Boolean = try { packageManager.getPackageInfo("com.google.android.gms", 0); true } catch (_: Exception) { false }
    private fun showReport(report: String, title: String = "Диагностика") {
        if (isFinishing || isDestroyed) return
        val text = TextView(this).apply { this.text = report; textSize = 14f; setTextIsSelectable(true); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("Закрыть", null).setNeutralButton("Копировать") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MonjaroTube", report))
            }.show()
    }
    @Deprecated("Android 11 prototype back handling")
    override fun onBackPressed() { if (fullscreen != null) hideFullscreen() else if (browser?.canGoBack() == true) browser?.goBack() else super.onBackPressed() }
    override fun onPause() {
        if (NavigationPolicy.isContentPage(browser?.url.orEmpty())) browser?.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())", null)
        CookieManager.getInstance().flush()
        browser?.onPause(); super.onPause()
    }
    override fun onResume() { super.onResume(); browser?.onResume() }
    override fun onSaveInstanceState(outState: Bundle) {
        if (NavigationPolicy.isContentPage(currentUrl)) outState.putString("page", currentUrl)
        super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ui.resize()
        ui.buildToolbar(
            search = { showSearch() },
            home = { navigate("https://m.youtube.com/") },
            reload = { reloadPage() },
            settings = { showSettings() })
    }
    override fun onDestroy() {
        generation++; handler.removeCallbacksAndMessages(null)
        cancelUpdateCheck()
        ui.destroy(); destroyBrowser(); disconnect()
        super.onDestroy()
    }
}
