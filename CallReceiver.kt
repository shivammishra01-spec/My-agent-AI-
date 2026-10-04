package com.shivam.autoanswer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    companion object { var handled = false }

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
        val pending = goAsync()
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({
            try {
                c.getSystemService(TelecomManager::class.java).acceptRingingCall()
            } catch (e: Exception) { }
            if (p.getBoolean("speaker", true)) {
                h.postDelayed({ speakerOn(c); pending.finish() }, 1500)
            } else pending.finish()
        }, p.getInt("delay", 3) * 1000L)
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
        } catch (e: Exception) { }
    }
}
