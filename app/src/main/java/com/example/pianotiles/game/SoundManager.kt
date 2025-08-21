package com.example.pianotiles.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

class SoundManager(private val context: Context, private val enabled: Boolean) {
    private var sp: SoundPool? = null
    private var tg: ToneGenerator? = null
    private var idPerfect = 0
    private var idGreat = 0
    private var idGood = 0
    private var idMiss = 0

    // Piano-like notes mapping (lane -> soundId)
    private val noteIds = IntArray(8) { 0 }
    private var bgId: Int = 0
    private var bgStreamId: Int = 0

    init {
        try {
            if (enabled) {
                sp = SoundPool.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setMaxStreams(8)
                    .build()
                // Try loading res/raw resources by name; fallback to tones
                idPerfect = loadRaw("sound_perfect")
                idGreat = loadRaw("sound_great")
                idGood = loadRaw("sound_good")
                idMiss = loadRaw("sound_miss")
                // Generate piano-like note samples and a soft background pad
                generateAndLoadPianoNotes()
                generateAndLoadBackground()
                tg = if (idPerfect == 0 && idGreat == 0 && idGood == 0 && idMiss == 0) ToneGenerator(AudioManager.STREAM_MUSIC, 60) else null
            }
        } catch (_: Throwable) {
            tg = if (enabled) ToneGenerator(AudioManager.STREAM_MUSIC, 60) else null
            sp = null
        }
    }

    private fun loadRaw(name: String): Int {
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        return if (resId != 0) sp?.load(context, resId, 1) ?: 0 else 0
    }

    fun play(j: GameEngine.Judgement) {
        try {
            if (!enabled) return
            val s = sp
            if (s != null) {
                val id = when (j) {
                    GameEngine.Judgement.PERFECT -> if (idPerfect != 0) idPerfect else idGreat
                    GameEngine.Judgement.GREAT -> if (idGreat != 0) idGreat else idGood
                    GameEngine.Judgement.GOOD -> if (idGood != 0) idGood else idGreat
                    GameEngine.Judgement.MISS -> idMiss
                    else -> 0
                }
                if (id != 0) {
                    s.play(id, 1f, 1f, 1, 0, 1f)
                    return
                }
            }
            tg?.startTone(
                when (j) {
                    GameEngine.Judgement.PERFECT -> ToneGenerator.TONE_PROP_BEEP2
                    GameEngine.Judgement.GREAT -> ToneGenerator.TONE_PROP_BEEP
                    GameEngine.Judgement.GOOD -> ToneGenerator.TONE_PROP_BEEP
                    GameEngine.Judgement.MISS -> ToneGenerator.TONE_PROP_NACK
                    else -> ToneGenerator.TONE_PROP_BEEP
                }, 60
            )
        } catch (_: Throwable) {
            // ignore audio errors
        }
    }

    fun playNote(laneIndex: Int, j: GameEngine.Judgement) {
        try {
            if (!enabled) return
            val s = sp ?: return
            val idx = (laneIndex % 4).coerceIn(0, 3)
            // Map 4 lanes to 4 musical scale notes within an octave: C4, E4, G4, C5
            val mapIndex = when (idx) {
                0 -> 0 // C4
                1 -> 2 // E4
                2 -> 4 // G4
                else -> 7 // C5
            }
            val soundId = noteIds.getOrNull(mapIndex) ?: 0
            if (soundId != 0) {
                val vol = when (j) {
                    GameEngine.Judgement.PERFECT -> 1.0f
                    GameEngine.Judgement.GREAT -> 0.85f
                    GameEngine.Judgement.GOOD -> 0.7f
                    else -> 0.6f
                }
                s.play(soundId, vol, vol, 1, 0, 1f)
            } else {
                // fallback
                play(j)
            }
        } catch (_: Throwable) {}
    }

    fun startBackgroundSoft() {
        try {
            val s = sp ?: return
            if (bgId != 0 && bgStreamId == 0) {
                bgStreamId = s.play(bgId, 0.18f, 0.18f, 0, -1, 1f)
            }
        } catch (_: Throwable) {}
    }

    fun stopBackgroundSoft() {
        try {
            val s = sp ?: return
            if (bgStreamId != 0) {
                s.stop(bgStreamId)
                bgStreamId = 0
            }
        } catch (_: Throwable) {}
    }

    fun release() {
        stopBackgroundSoft()
        sp?.release(); sp = null
        tg?.release(); tg = null
    }

    // ------- helpers to generate small WAVs and load into SoundPool -------
    private fun generateAndLoadPianoNotes() {
        val s = sp ?: return
        val cache = File(context.cacheDir, "pnotes").apply { mkdirs() }
        val sampleRate = 22050
        val durationSec = 0.35
        // C major scale starting at C4
        val freqs = doubleArrayOf(
            261.63, // C4
            293.66, // D4
            329.63, // E4
            349.23, // F4
            392.00, // G4
            440.00, // A4
            493.88, // B4
            523.25  // C5
        )
        for (i in freqs.indices) {
            val f = File(cache, "n$i.wav")
            try {
                if (!f.exists()) writeNoteWav(f, sampleRate, durationSec, freqs[i])
                val fd = java.io.FileInputStream(f).fd
                noteIds[i] = s.load(fd, 0L, f.length(), 1)
            } catch (_: Throwable) { noteIds[i] = 0 }
        }
    }

    private fun generateAndLoadBackground() {
        val s = sp ?: return
        val cache = File(context.cacheDir, "pnotes").apply { mkdirs() }
        val f = File(cache, "bg.wav")
        val sampleRate = 22050
        val durationSec = 2.0
        try {
            if (!f.exists()) writePadWav(f, sampleRate, durationSec)
            val fd = java.io.FileInputStream(f).fd
            bgId = s.load(fd, 0L, f.length(), 1)
        } catch (_: Throwable) { bgId = 0 }
    }

    private fun writeNoteWav(file: File, sampleRate: Int, durationSec: Double, freq: Double) {
        val total = (sampleRate * durationSec).toInt()
        val data = ShortArray(total)
        val twoPiF = 2.0 * PI * freq
        val attack = (0.01 * sampleRate).toInt()
        val decay = (0.25 * sampleRate).toInt()
        val release = total - attack - decay
        for (n in 0 until total) {
            val t = n / sampleRate.toDouble()
            val s1 = sin(twoPiF * t)
            val s2 = sin(2 * twoPiF * t) * 0.4
            val s3 = sin(3 * twoPiF * t) * 0.25
            val raw = (s1 + s2 + s3) / 1.65
            val env = when {
                n < attack -> n / attack.toDouble()
                n < attack + decay -> 1.0 - (n - attack) / decay.toDouble() * 0.3
                else -> 0.7 * kotlin.math.exp(-3.0 * (n - attack - decay) / release.toDouble())
            }
            val v = (raw * env * 32767.0 * 0.9).toInt().coerceIn(-32768, 32767)
            data[n] = v.toShort()
        }
        writeWav16le(file, sampleRate, data)
    }

    private fun writePadWav(file: File, sampleRate: Int, durationSec: Double) {
        val total = (sampleRate * durationSec).toInt()
        val data = ShortArray(total)
        val f1 = 196.0 // G3
        val f2 = 261.63 // C4
        val f3 = 392.0 // G4
        for (n in 0 until total) {
            val t = n / sampleRate.toDouble()
            val env = 0.4 * (1.0 - (n.toDouble() / total))
            val s = (sin(2 * PI * f1 * t) + sin(2 * PI * f2 * t) * 0.6 + sin(2 * PI * f3 * t) * 0.5) / 2.1
            val v = (s * env * 32767.0 * 0.5).toInt().coerceIn(-32768, 32767)
            data[n] = v.toShort()
        }
        writeWav16le(file, sampleRate, data)
    }

    private fun writeWav16le(file: File, sampleRate: Int, samples: ShortArray) {
        FileOutputStream(file).use { out ->
            val numChannels = 1
            val bitsPerSample = 16
            val byteRate = sampleRate * numChannels * bitsPerSample / 8
            val subchunk2Size = samples.size * numChannels * bitsPerSample / 8
            val chunkSize = 36 + subchunk2Size
            fun writeLE32(v: Int) { out.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())) }
            fun writeLE16(v: Int) { out.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())) }
            out.write("RIFF".toByteArray())
            writeLE32(chunkSize)
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            writeLE32(16) // PCM chunk size
            writeLE16(1)  // PCM
            writeLE16(numChannels)
            writeLE32(sampleRate)
            writeLE32(byteRate)
            writeLE16(numChannels * bitsPerSample / 8)
            writeLE16(bitsPerSample)
            out.write("data".toByteArray())
            writeLE32(subchunk2Size)
            // samples
            val buf = java.nio.ByteBuffer.allocate(samples.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buf.asShortBuffer().put(samples)
            out.write(buf.array())
        }
    }
}
