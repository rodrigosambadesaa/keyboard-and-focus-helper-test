package dev.rodrigosambade.keyboardtest

import KeyboardAndFocusHelper
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Manual end-to-end experiment; real IME behavior also needs emulator/device tests. */
class MainActivity : Activity() {
    private var subscription: KeyboardAndFocusHelper.KeyboardVisibilitySubscription? = null
    private lateinit var input: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply { text = "Awaiting keyboard state" }
        input = EditText(this).apply {
            hint = "Enter sample text"
            setSingleLine(true)
            setText("Keyboard and focus helper")
        }
        column.addView(status)
        column.addView(input)
        fun addButton(label: String, action: () -> Unit) {
            column.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        addButton("Show keyboard") { report("show", KeyboardAndFocusHelper.showKeyboard(input)) }
        addButton("Focus, cursor to end, show") {
            report("focus+show", KeyboardAndFocusHelper.requestFocusAndShowKeyboard(input))
        }
        addButton("Hide keyboard") { report("hide", KeyboardAndFocusHelper.hideKeyboard(this)) }
        addButton("Clear focus and hide") {
            report("clear+hide", KeyboardAndFocusHelper.clearFocusAndHideKeyboard(this))
        }
        addButton("Read keyboard state") { refreshStatus() }
        addButton("Toggle adjustResize / adjustPan") {
            val mode = if (window.attributes.softInputMode and 0xf0 ==
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) {
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            } else {
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            window.setSoftInputMode(mode)
            refreshStatus()
        }
        setContentView(ScrollView(this).apply { fillViewport = true; addView(column) })
        subscription = KeyboardAndFocusHelper.setOnKeyboardVisibilityListener(window.decorView) { visible, height ->
            status.text = "IME visible=$visible height=${height}px; focus=${input.hasFocus()}"
        }
    }

    private fun report(operation: String, scheduled: Boolean) {
        status.text = "$operation dispatched/scheduled=$scheduled"
        input.postDelayed({ refreshStatus() }, 350)
    }

    private fun refreshStatus() {
        status.text = "IME visible=${KeyboardAndFocusHelper.isKeyboardVisible(input)} " +
            "height=${KeyboardAndFocusHelper.getKeyboardHeightPx(input)}px " +
            "focus=${input.hasFocus()} subscriptionRemoved=${subscription?.isRemoved()}"
    }

    override fun onDestroy() {
        subscription?.remove()
        subscription = null
        super.onDestroy()
    }
}
