package ru.geelymonjaro.monjarotube

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.*
import android.widget.*

/** Native chrome kept separate from the YouTube document and its styles. */
internal class TubeUi(private val activity: Activity) {
    val bg = Color.rgb(15, 15, 15)
    val surface = Color.rgb(31, 31, 31)
    val line = Color.rgb(49, 49, 49)
    val white = Color.rgb(241, 241, 241)
    val muted = Color.rgb(166, 166, 166)
    val red = Color.rgb(255, 48, 64)
    val green = Color.rgb(88, 204, 149)
    val root = vertical().apply { setBackgroundColor(bg) }
    val content = FrameLayout(activity)
    private val toolbar = horizontal().apply { setPadding(dp(10), 0, dp(10), 0) }
    private val route = text("Подключение", 12, green, true)
    private val accountOrigin = text("accounts.google.com  ·  Вход Google", 12, muted).apply {
        setPadding(dp(20), dp(8), dp(20), dp(8)); visibility = View.GONE
        setBackgroundColor(surface)
    }
    private val loading = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; progressTintList = ColorStateList.valueOf(red)
        progressBackgroundTintList = ColorStateList.valueOf(line)
    }
    private val message = vertical().apply {
        gravity = Gravity.CENTER; setPadding(dp(32), dp(28), dp(32), dp(28))
        background = shape(surface, 24)
    }
    private val messageTitle = text("Подключаем YouTube", 23, white, true)
    private val messageBody = text("Запускаем встроенный обход…", 14, muted)
    private val retry = action("Повторить подключение") {}
    private val messageSpinner = ProgressBar(activity).apply { indeterminateTintList = ColorStateList.valueOf(red) }
    private var sheet: Dialog? = null

    init {
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(60)))
        root.addView(loading, LinearLayout.LayoutParams(-1, dp(2)))
        root.addView(accountOrigin)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        message.addView(messageSpinner, LinearLayout.LayoutParams(dp(28), dp(28)).apply { bottomMargin = dp(20) })
        message.addView(messageTitle)
        message.addView(messageBody, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        messageBody.gravity = Gravity.CENTER
        message.addView(retry, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(24) })
        content.addView(message, FrameLayout.LayoutParams(dp(420).coerceAtMost(activity.resources.displayMetrics.widthPixels - dp(40)), -2, Gravity.CENTER))
    }

    fun buildToolbar(home: () -> Unit, reload: () -> Unit, search: () -> Unit, settings: () -> Unit) {
        toolbar.removeAllViews()
        toolbar.addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f))
        toolbar.addView(icon("search", "Поиск YouTube", search))
        if (activity.resources.configuration.screenWidthDp >= 600) {
            route.apply { gravity = Gravity.CENTER; setPadding(dp(14), 0, dp(14), 0); background = shape(Color.rgb(26, 44, 36), 18); setOnClickListener { settings() } }
            toolbar.addView(route, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(12) })
            toolbar.addView(icon("home", "Главная YouTube", home))
            toolbar.addView(icon("reload", "Обновить страницу", reload))
        }
        toolbar.addView(icon("settings", "Настройки MonjaroTube", settings))
    }

    fun connecting(retryAction: () -> Unit, title: String = "Подключаем YouTube", description: String = "Соединение настроится автоматически") {
        route.text = "Подключение…"; route.setTextColor(muted)
        messageTitle.text = title
        messageBody.text = description
        messageSpinner.visibility = View.VISIBLE; retry.visibility = View.GONE
        retry.setOnClickListener { retryAction() }
        message.visibility = View.VISIBLE; message.bringToFront()
        loading.visibility = View.VISIBLE
    }

    fun connected(mode: Int) {
        route.text = when (mode) { 1 -> "Обход включён"; 2 -> "Внешний прокси"; else -> "Без обхода" }
        route.setTextColor(if (mode == 1) green else white)
        message.visibility = View.GONE
    }

    fun error(reason: String, retryAction: () -> Unit, title: String = "Не удалось подключиться", retryLabel: String = "Повторить подключение") {
        route.text = "Нет соединения"; route.setTextColor(red)
        messageTitle.text = title
        messageBody.text = reason
        messageSpinner.visibility = View.GONE; retry.visibility = View.VISIBLE
        retry.text = retryLabel
        retry.setOnClickListener { retryAction() }
        message.visibility = View.VISIBLE; message.bringToFront()
        loading.visibility = View.INVISIBLE
    }

    fun progress(value: Int) { loading.progress = value; loading.visibility = if (value in 0..99) View.VISIBLE else View.INVISIBLE }

    fun settings(selectedTab: Int = 0, render: (Int, LinearLayout) -> Unit) {
        sheet?.dismiss()
        val dialog = Dialog(activity)
        sheet = dialog
        val panel = vertical().apply { background = shape(bg, 24) }
        val heading = horizontal().apply { setPadding(dp(24), dp(18), dp(12), dp(12)) }
        val titles = vertical()
        titles.addView(text("Настройки", 26, white, true))
        titles.addView(text("MonjaroTube", 13, muted), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        heading.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(icon("close", "Закрыть настройки") { dialog.dismiss() })
        panel.addView(heading)
        val tabs = horizontal().apply { setPadding(dp(20), dp(8), dp(20), dp(16)) }
        panel.addView(tabs)
        val scroll = ScrollView(activity).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        val body = vertical().apply { setPadding(dp(24), dp(8), dp(24), dp(28)) }
        scroll.addView(body)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = text("MonjaroTube  ${BuildConfig.VERSION_NAME.substringBefore('-')}", 12, muted).apply { setPadding(dp(24), dp(12), dp(24), dp(20)) }
        panel.addView(footer)
        var tab = selectedTab
        fun draw() {
            tabs.removeAllViews(); body.removeAllViews()
            listOf("Соединение", "Просмотр", "Приложение").forEachIndexed { index, label ->
                tabs.addView(text(label, 13, if (tab == index) white else muted, tab == index).apply {
                    gravity = Gravity.CENTER; background = ripple(if (tab == index) line else bg, 20)
                    setOnClickListener { tab = index; draw(); scroll.scrollTo(0, 0) }
                }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (index != 2) marginEnd = dp(6) })
            }
            render(tab, body)
        }
        draw()
        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.62f)
            setGravity(Gravity.END)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
        dialog.window?.setLayout(dp(464).coerceAtMost(activity.resources.displayMetrics.widthPixels - dp(16)), -1)
    }

    fun accountPage(host: String?) {
        accountOrigin.text = "$host  ·  Вход Google"
        accountOrigin.visibility = if (host != null) View.VISIBLE else View.GONE
    }
    fun resize() {
        closeSettings()
        message.layoutParams = (message.layoutParams as FrameLayout.LayoutParams).apply {
            width = dp(420).coerceAtMost(activity.resources.displayMetrics.widthPixels - dp(40))
        }
    }
    fun closeSettings() { sheet?.dismiss(); sheet = null }
    fun destroy() { closeSettings() }
    fun section(parent: LinearLayout, title: String, subtitle: String? = null) {
        parent.addView(text(title, 20, white, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
        if (subtitle != null) parent.addView(text(subtitle, 13, muted), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
    }
    fun card(parent: LinearLayout): LinearLayout = vertical().apply {
        background = shape(surface, 18); setPadding(dp(16), dp(4), dp(16), dp(4))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    fun note(parent: LinearLayout, value: String) { parent.addView(text(value, 13, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(18) }) }
    fun choice(parent: LinearLayout, title: String, description: String, selected: Boolean, recommended: Boolean = false, onClick: () -> Unit) {
        val row = horizontal().apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = ripple(if (selected) Color.rgb(45, 28, 31) else surface, 18, if (selected) Color.rgb(125, 45, 54) else line)
            setOnClickListener { onClick() }
            contentDescription = "$title. $description. ${if (selected) "Выбрано" else "Не выбрано"}"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true; minimumHeight = dp(76)
        }
        val labels = vertical().apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        labels.addView(text(title, 15, white, true))
        labels.addView(text(description, 12, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        if (recommended) labels.addView(text("РЕКОМЕНДУЕТСЯ", 10, red, true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageView(activity).apply { setImageDrawable(Glyph(if (selected) "checked" else "radio", if (selected) red else muted)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(16) })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }
    fun item(parent: LinearLayout, glyph: String, title: String, description: String, onClick: () -> Unit) {
        val row = horizontal().apply { minimumHeight = dp(76); setPadding(0, dp(12), 0, dp(12)); background = ripple(surface, 12); setOnClickListener { onClick() } }
        row.addView(ImageView(activity).apply { setImageDrawable(Glyph(glyph, white)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(14) })
        val labels = vertical(); labels.addView(text(title, 14, white, true)); labels.addView(text(description, 12, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageView(activity).apply { setImageDrawable(Glyph("chevron", muted)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(8) })
        parent.addView(row)
    }
    fun toggle(parent: LinearLayout, title: String, description: String, checked: Boolean, changed: (Boolean) -> Unit) {
        val row = horizontal().apply { minimumHeight = dp(82); setPadding(0, dp(12), 0, dp(12)) }
        val labels = vertical(); labels.addView(text(title, 14, white, true)); labels.addView(text(description, 12, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(activity).apply {
            isChecked = checked; contentDescription = title; showText = false
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(red, muted))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.rgb(99, 36, 43), line))
            setPadding(dp(16), dp(8), 0, dp(8)); minimumHeight = dp(48)
            setOnCheckedChangeListener { _, value -> changed(value) }
        })
        parent.addView(row)
    }
    fun divider(parent: LinearLayout) { parent.addView(View(activity).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1))) }
    fun action(label: String, action: () -> Unit): TextView = text(label, 14, bg, true).apply {
        gravity = Gravity.CENTER; background = ripple(white, 24); minimumHeight = dp(48); setOnClickListener { action() }
    }
    fun text(value: String, size: Int, color: Int = white, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); includeFontPadding = false
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    fun icon(name: String, label: String, action: () -> Unit) = ImageButton(activity).apply {
        setImageDrawable(Glyph(name, white)); contentDescription = label
        background = ripple(bg, 24); setPadding(dp(13), dp(13), dp(13), dp(13))
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)); setOnClickListener { action() }
        isFocusable = true
    }
    fun vertical() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    fun horizontal() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    fun shape(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }
    private fun ripple(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = RippleDrawable(ColorStateList.valueOf(0x24FFFFFF), shape(color, radius, stroke), null)
}

/** Small consistent stroke icon set, independent of platform icon fonts. */
private class Glyph(private val name: String, private val tint: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; strokeWidth = 1.8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun draw(canvas: Canvas) {
        paint.color = tint; paint.style = Paint.Style.STROKE
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(vararg xy: Float) { val path = Path(); path.moveTo(xy[0], xy[1]); for(i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i+1]); canvas.drawPath(path, paint) }
        when(name) {
            "search" -> { canvas.drawCircle(10f,10f,6.5f,paint); line(15f,15f,21f,21f) }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            "home" -> { line(3f,11f,12f,3f,21f,11f); line(5f,10f,5f,21f,10f,21f,10f,15f,14f,15f,14f,21f,19f,21f,19f,10f) }
            "reload" -> { canvas.drawArc(4f,4f,20f,20f,40f,280f,false,paint); line(20f,3f,20f,9f,14f,9f) }
            "settings" -> { line(4f,6f,20f,6f); line(4f,12f,20f,12f); line(4f,18f,20f,18f); paint.style=Paint.Style.FILL; canvas.drawCircle(9f,6f,2.8f,paint);canvas.drawCircle(16f,12f,2.8f,paint);canvas.drawCircle(8f,18f,2.8f,paint) }
            "chevron" -> line(9f,5f,16f,12f,9f,19f)
            "radio", "checked" -> { canvas.drawCircle(12f,12f,9f,paint); if(name=="checked") { paint.style=Paint.Style.FILL;canvas.drawCircle(12f,12f,5f,paint) } }
            "account" -> { canvas.drawCircle(12f,8f,4f,paint);canvas.drawArc(4f,13f,20f,27f,185f,170f,false,paint) }
            "link" -> { canvas.drawRoundRect(3f,8f,13f,17f,4f,4f,paint);canvas.drawRoundRect(11f,4f,21f,13f,4f,4f,paint) }
            "network" -> { canvas.drawCircle(12f,12f,9f,paint);line(3f,12f,21f,12f);canvas.drawOval(8f,3f,16f,21f,paint) }
            "info" -> { canvas.drawCircle(12f,12f,9f,paint);line(12f,11f,12f,17f);canvas.drawCircle(12f,7f,0.7f,paint) }
            else -> { canvas.drawRoundRect(4f,3f,20f,21f,3f,3f,paint);line(8f,8f,16f,8f);line(8f,12f,16f,12f);line(8f,16f,13f,16f) }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
