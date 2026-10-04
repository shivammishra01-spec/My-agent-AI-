package com.shivam.autoanswer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import java.util.Locale

const val DEFAULT_GREETING = "नमस्ते, बताइए, क्या काम है?"

class CallReceiver : BroadcastReceiver() {
    companion object {
        var handled = false
        private var tts: TextToSpeech? = null
        private var ttsReady = false
    }

    override fun onReceive(c: Context, i: Intent) {
        if (i.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = i.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) { handled = false; return }

        val p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        if (!p.getBoolean("on", false) || handled) return
        if (c.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED) return

        if (p.getBoolean("onlySel", false)) {
            // The first RINGING broadcast has no number; wait for the one that does.
            @Suppress("DEPRECATION")
            val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
            val key = num.filter { it.isDigit() }.takeLast(10)
            val allowed = p.getStringSet("contacts", emptySet())!!.any {
                it.substringAfter('|').filter { ch -> ch.isDigit() }.takeLast(10) == key
            }
            if (key.isEmpty() || !allowed) return
        }

        handled = true // answer each call only once
        val greet = p.getBoolean("greetOn", true)
        val text = p.getString("greet", DEFAULT_GREETING) ?: DEFAULT_GREETING
        if (greet) initTts(c.applicationContext, text)

        val pending = goAsync()
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({
            try {
                c.getSystemService(TelecomManager::class.java).acceptRingingCall()
            } catch (e: Exception) { }
            if (greet || p.getBoolean("speaker", true)) {
                h.postDelayed({
                    speakerOn(c)
                    if (greet) speak(text)
                    pending.finish()
                }, 1200)
            } else pending.finish()
        }, p.getInt("delay", 3) * 1000L)
    }

    private fun initTts(c: Context, text: String) {
        ttsReady = false
        tts?.shutdown()
        tts = TextToSpeech(c) { status ->
            val t = tts ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                t.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                val hindi = text.any { it in '\u0900'..'\u097F' }
                t.language = if (hindi) Locale("hi", "IN") else Locale.getDefault()
                ttsReady = true
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "greet")
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "greet")
            }, 1500)
        }
    }

    private fun speakerOn(c: Context) {
        try {
            val am = c.getSystemService(AudioManager::class.java)
            am.mode = AudioManager.MODE_IN_CALL
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
        } catch (e: Exception) { }
    }
}
