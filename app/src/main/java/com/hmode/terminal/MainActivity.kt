package com.hmode.terminal

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val terminal = TextView(this)
        terminal.text = "Terminal App\nAndroid Terminal Interface"
        terminal.textSize = 22f
        setContentView(terminal)
    }
}
