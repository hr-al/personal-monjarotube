package ru.geelymonjaro.monjarotube

import android.content.Context
import android.text.InputType
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/** Ask the IME after Chromium has established an editable input connection. */
internal class KeyboardWebView(context: Context) : WebView(context) {
    private var editing = false
    private val showKeyboard = Runnable {
        if (editing && isAttachedToWindow && hasFocus() && hasWindowFocus())
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, 0)
    }
    init { isFocusable = true; isFocusableInTouchMode = true }
    private fun scheduleKeyboard() { removeCallbacks(showKeyboard); postDelayed(showKeyboard, 150) }
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        editing = connection != null && outAttrs.inputType != InputType.TYPE_NULL
        if (editing) scheduleKeyboard() else removeCallbacks(showKeyboard)
        return connection
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && hitTestResult.type == HitTestResult.EDIT_TEXT_TYPE) {
            requestFocus()
            scheduleKeyboard()
        }
        return handled
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && editing) scheduleKeyboard()
    }
    override fun onDetachedFromWindow() { removeCallbacks(showKeyboard); super.onDetachedFromWindow() }
}
