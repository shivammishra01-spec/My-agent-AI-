package com.shivam.autoanswer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var p: SharedPreferences
    private lateinit var list: TextView
    private var tts: TextToSpeech? = null
    private var ttsOk = false

    // Type a sentence and speak it. During a call it goes out through the speaker,
    // and the caller hears it through the phone's mic.
    private fun speakText(text: String) {
        if (text.isBlank() || !ttsOk) return
        try {
            val am = getSystemService(AudioManager::class.java)
            if (am.mode == AudioManager.MODE_IN_CALL) {
                if (Build.VERSION.SDK_INT >= 31) {
                    am.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { am.setCommunicationDevice(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                }
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                    am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0)
            }
        } catch (e: Exception) { }
        val t = tts ?: return
        t.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        val hindi = text.any { it in '\u0900'..'\u097F' }
        t.language = if (hindi) Locale("hi", "IN") else Locale.getDefault()
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "typed")
    }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    private fun askPerms() = requestPermissions(arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_CALL_LOG), 1)

    private fun showList() {
        val s = p.getStringSet("contacts", emptySet())!!
        list.text = if (s.isEmpty()) "No contacts selected"
        else s.joinToString("\n") { "• " + it.substringBefore('|') }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = getSharedPreferences("cfg", MODE_PRIVATE)
        val pad = (24 * resources.displayMetrics.density).toInt()

        tts = TextToSpeech(this) { ttsOk = it == TextToSpeech.SUCCESS }
        val title = TextView(this).apply { text = "Auto Answer"; textSize = 28f }
        val typed = EditText(this).apply {
            hint = "Type here, then tap Speak (use during a call)"; minLines = 2
            setPadding(0, pad / 2, 0, pad / 2)
        }
        val speakBtn = Button(this).apply {
            text = "🔊 Speak on call"
            setOnClickListener { speakText(typed.text.toString()); typed.setText("") }
        }
        val sw = Switch(this).apply {
            text = "Answer incoming calls"; textSize = 20f
            isChecked = p.getBoolean("on", false); setPadding(0, pad, 0, pad / 2)
        }
        val only = Switch(this).apply {
            text = "Only selected contacts"; textSize = 18f
            isChecked = p.getBoolean("onlySel", false); setPadding(0, pad / 2, 0, pad / 2)
        }
        val spk = Switch(this).apply {
            text = "Speaker ON after answering"; textSize = 18f
            isChecked = p.getBoolean("speaker", true); setPadding(0, pad / 2, 0, pad / 2)
        }
        val greetSw = Switch(this).apply {
            text = "Speak a greeting after answering"; textSize = 18f
            isChecked = p.getBoolean("greetOn", true); setPadding(0, pad / 2, 0, pad / 2)
        }
        val greetEt = EditText(this).apply {
            setText(p.getString("greet", DEFAULT_GREETING))
            hint = "Greeting to speak"; minLines = 2
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    p.edit().putString("greet", s.toString()).apply()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        val label = TextView(this).apply { textSize = 16f; setPadding(0, pad / 2, 0, 0) }
        val bar = SeekBar(this).apply { max = 6; progress = p.getInt("delay", 3) }
        val add = Button(this).apply {
            text = "+ Add contact"
            setOnClickListener { startActivityForResult(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI), 7) }
        }
        val clear = Button(this).apply {
            text = "Clear list"
            setOnClickListener { p.edit().remove("contacts").apply(); showList() }
        }
        list = TextView(this).apply { textSize = 16f; setPadding(0, pad / 2, 0, pad / 2) }
        val hint = TextView(this).apply {
            text = "Allow phone, call log and answer-call permissions. With \"Only selected contacts\" on and an empty list, no call is answered. Keep this app unrestricted in battery settings. The greeting is played through the speaker, so the caller hears it through your phone's mic."
            textSize = 13f; setPadding(0, pad / 2, 0, 0)
        }

        fun refresh() { label.text = "Answer after ${bar.progress} seconds" }
        refresh(); showList()

        sw.setOnCheckedChangeListener { _, on ->
            p.edit().putBoolean("on", on).apply(); if (on) askPerms()
        }
        only.setOnCheckedChangeListener { _, on ->
            p.edit().putBoolean("onlySel", on).apply(); if (on) askPerms()
        }
        spk.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("speaker", on).apply() }
        greetSw.setOnCheckedChangeListener { _, on -> p.edit().putBoolean("greetOn", on).apply() }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                p.edit().putInt("delay", v).apply(); refresh()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            listOf(title, typed, speakBtn, sw, only, spk, greetSw, greetEt, label, bar, add, list, clear, hint)
                .forEach { addView(it) }
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        val uri = d?.data ?: return
        if (rc != 7 || res != RESULT_OK) return
        contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use {
            if (it.moveToFirst()) {
                val s = p.getStringSet("contacts", emptySet())!!.toMutableSet()
                s.add("${it.getString(0)}|${it.getString(1)}")
                p.edit().putStringSet("contacts", s).apply(); showList()
            }
        }
    }
}
