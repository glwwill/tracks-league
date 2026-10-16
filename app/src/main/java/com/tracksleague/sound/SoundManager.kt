package com.tracksleague.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

class SoundManager(context: Context) {
    private val prefs = context.getSharedPreferences("tl_prefs", Context.MODE_PRIVATE)
    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("sound_on", true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private val sampleRate = 22050

    fun toggleSound(): Boolean {
        val next = !_soundEnabled.value
        _soundEnabled.value = next
        prefs.edit().putBoolean("sound_on", next).apply()
        if (next) soundOn()
        return next
    }

    private val pentatonic = intArrayOf(0, 2, 4, 7, 9)

    private fun noteFreq(semitones: Double): Double =
        523.25 * 2.0.pow(semitones / 12.0) // C5 base

    fun place(n: Int) {
        if (!_soundEnabled.value) return
        val i = (n - 1).coerceAtLeast(0)
        val octave = (i / 5).coerceAtMost(2)
        val semitones = pentatonic[i % 5] + octave * 12.0 - 12.0
        val freq = noteFreq(semitones)
        playTone(freq = freq, durationSec = 0.11, waveform = Waveform.TRIANGLE, volume = 0.22f)
    }

    fun erase() {
        if (!_soundEnabled.value) return
        playGlideTone(startFreq = 320.0, endFreq = 190.0, durationSec = 0.09, waveform = Waveform.SINE, volume = 0.18f)
    }

    fun mark() {
        if (!_soundEnabled.value) return
        playTone(freq = 240.0, durationSec = 0.06, waveform = Waveform.SQUARE, volume = 0.08f)
    }

    fun unmark() {
        if (!_soundEnabled.value) return
        playTone(freq = 190.0, durationSec = 0.06, waveform = Waveform.SQUARE, volume = 0.07f)
    }

    fun click() {
        if (!_soundEnabled.value) return
        playTone(freq = 660.0, durationSec = 0.05, waveform = Waveform.SINE, volume = 0.12f)
    }

    fun thunk() {
        if (!_soundEnabled.value) return
        playGlideTone(startFreq = 170.0, endFreq = 80.0, durationSec = 0.14, waveform = Waveform.SINE, volume = 0.26f)
    }

    fun solve() {
        if (!_soundEnabled.value) return
        scope.launch {
            val semitonesList = listOf(0.0, 4.0, 7.0, 12.0)
            for (idx in semitonesList.indices) {
                val s = semitonesList[idx]
                val dur = if (idx == 3) 0.40 else 0.12
                playTone(freq = noteFreq(s), durationSec = dur, waveform = Waveform.TRIANGLE, volume = 0.25f)
                kotlinx.coroutines.delay((0.09 * 1000).toLong())
            }
            playTone(freq = noteFreq(16.0), durationSec = 0.45, waveform = Waveform.SINE, volume = 0.18f)
        }
    }

    fun soundOn() {
        scope.launch {
            playTone(freq = noteFreq(0.0), durationSec = 0.08, waveform = Waveform.TRIANGLE, volume = 0.2f)
            kotlinx.coroutines.delay(80)
            playTone(freq = noteFreq(7.0), durationSec = 0.12, waveform = Waveform.TRIANGLE, volume = 0.2f)
        }
    }

    private enum class Waveform { SINE, TRIANGLE, SQUARE }

    private fun playTone(freq: Double, durationSec: Double, waveform: Waveform, volume: Float) {
        scope.launch {
            val numSamples = (durationSec * sampleRate).toInt().coerceAtLeast(1)
            val buffer = ShortArray(numSamples)
            val attackSamples = (0.012 * sampleRate).toInt().coerceAtLeast(1)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val rawWave = when (waveform) {
                    Waveform.SINE -> sin(2.0 * PI * freq * t)
                    Waveform.TRIANGLE -> {
                        val phase = (freq * t) % 1.0
                        if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
                    }
                    Waveform.SQUARE -> {
                        val phase = (freq * t) % 1.0
                        if (phase < 0.5) 1.0 else -1.0
                    }
                }

                // Envelope: quick attack, exponential decay
                val envelope = when {
                    i < attackSamples -> i.toDouble() / attackSamples
                    else -> exp(-4.0 * (i - attackSamples).toDouble() / (numSamples - attackSamples))
                }

                val sample = (rawWave * envelope * volume * Short.MAX_VALUE).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            renderAudio(buffer)
        }
    }

    private fun playGlideTone(startFreq: Double, endFreq: Double, durationSec: Double, waveform: Waveform, volume: Float) {
        scope.launch {
            val numSamples = (durationSec * sampleRate).toInt().coerceAtLeast(1)
            val buffer = ShortArray(numSamples)
            var currentPhase = 0.0

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val currentFreq = startFreq + (endFreq - startFreq) * progress
                currentPhase += 2.0 * PI * currentFreq / sampleRate

                val rawWave = when (waveform) {
                    Waveform.SINE -> sin(currentPhase)
                    Waveform.TRIANGLE -> {
                        val phaseNorm = (currentPhase / (2.0 * PI)) % 1.0
                        if (phaseNorm < 0.5) 4.0 * phaseNorm - 1.0 else 3.0 - 4.0 * phaseNorm
                    }
                    Waveform.SQUARE -> {
                        val phaseNorm = (currentPhase / (2.0 * PI)) % 1.0
                        if (phaseNorm < 0.5) 1.0 else -1.0
                    }
                }

                val envelope = exp(-3.0 * progress)
                val sample = (rawWave * envelope * volume * Short.MAX_VALUE).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            renderAudio(buffer)
        }
    }

    private fun renderAudio(buffer: ShortArray) {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack(
                audioAttributes,
                audioFormat,
                buffer.size * 2,
                AudioTrack.MODE_STATIC,
                AudioTrack.AUDIO_SESSION_ID_GENERATE
            )

            track.write(buffer, 0, buffer.size)
            track.play()
            track.setNotificationMarkerPosition(buffer.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    t?.release()
                }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
        } catch (_: Exception) {
            // Audio error non-critical
        }
    }
}
