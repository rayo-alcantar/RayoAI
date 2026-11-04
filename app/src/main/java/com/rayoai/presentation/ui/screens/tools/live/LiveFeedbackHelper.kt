package com.rayoai.presentation.ui.screens.tools.live

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Provee retroalimentación auditiva (earcons con ToneGenerator) y háptica (vibración)
 * para usuarios en el modo Live, especialmente crucial para usuarios ciegos o con baja visión.
 */
class LiveFeedbackHelper(private val context: Context) {

    companion object {
        private const val TAG = "LiveFeedbackHelper"
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo inicializar ToneGenerator", e)
        null
    }

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo obtener el servicio de vibración", e)
        null
    }

    fun onStarting() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 100)
            vibratePulse(50)
        } catch (e: Exception) {
            Log.w(TAG, "Error en feedback onStarting", e)
        }
    }

    fun onConnected() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            vibratePulse(100)
        } catch (e: Exception) {
            Log.w(TAG, "Error en feedback onConnected", e)
        }
    }

    fun onDisconnected() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
            vibratePulse(120)
        } catch (e: Exception) {
            Log.w(TAG, "Error en feedback onDisconnected", e)
        }
    }

    fun onPttPress() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
            vibrateTick()
        } catch (e: Exception) {
            Log.w(TAG, "Error en feedback onPttPress", e)
        }
    }

    fun onPttRelease() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 70)
            vibrateTick()
        } catch (e: Exception) {
            Log.w(TAG, "Error en feedback onPttRelease", e)
        }
    }

    fun onError() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 350)
            vibrateError()
        } catch (e: Exception) {
            Log.w(TAG, "Error en feedback onError", e)
        }
    }

    private fun vibratePulse(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Ignorar fallos de vibrador en dispositivos sin motor háptico
        }
    }

    private fun vibrateTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vibratePulse(25)
            }
        } catch (e: Exception) {
            vibratePulse(25)
        }
    }

    private fun vibrateError() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 80, 80, 160)
                val amplitudes = intArrayOf(0, 200, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 80, 80, 160), -1)
            }
        } catch (e: Exception) {
            vibratePulse(200)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando ToneGenerator", e)
        }
    }
}
