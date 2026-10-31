package com.saidjte.schoolradio.desktop.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine

/** تعداد أجهزة إدخال الصوت (الميكروفونات) المتاحة في النظام وفتحها. */
object AudioDevices {

    data class MicDevice(val mixerInfo: Mixer.Info?, val name: String)

    val CANDIDATE_FORMATS: List<AudioFormat> = listOf(
        AudioFormat(44100f, 16, 1, true, false),
        AudioFormat(48000f, 16, 1, true, false),
        AudioFormat(44100f, 16, 2, true, false),
        AudioFormat(48000f, 16, 2, true, false)
    )

    /** كل مزيج (Mixer) يدعم خط التقاط بصيغة من الصيغ المرشحة. */
    fun listMics(): List<MicDevice> {
        val out = mutableListOf<MicDevice>()
        for (mi in AudioSystem.getMixerInfo()) {
            try {
                val mixer = AudioSystem.getMixer(mi)
                val supportsAny = CANDIDATE_FORMATS.any { f ->
                    try {
                        mixer.isLineSupported(DataLine.Info(TargetDataLine::class.java, f))
                    } catch (_: Exception) {
                        false
                    }
                }
                if (supportsAny) {
                    out += MicDevice(mi, mi.name.trim().ifEmpty { "جهاز إدخال" })
                }
            } catch (_: Exception) {
            }
        }
        if (out.isEmpty()) out += MicDevice(null, "الميكروفون الافتراضي")
        return out
    }

    /** اختيار أول صيغة يدعمها الجهاز من قائمة الصيغ المرشحة. */
    fun pickFormat(mixerInfo: Mixer.Info?): AudioFormat {
        for (f in CANDIDATE_FORMATS) {
            try {
                val supported = if (mixerInfo != null) {
                    AudioSystem.getMixer(mixerInfo).isLineSupported(DataLine.Info(TargetDataLine::class.java, f))
                } else {
                    AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, f))
                }
                if (supported) return f
            } catch (_: Exception) {
            }
        }
        return AudioFormat(44100f, 16, 1, true, false)
    }

    /** فتح خط التقاط على الجهاز المحدد (أو الافتراضي عند null). */
    fun openTargetLine(mixerInfo: Mixer.Info?, format: AudioFormat): TargetDataLine {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        return if (mixerInfo != null) {
            AudioSystem.getMixer(mixerInfo).getLine(info) as TargetDataLine
        } else {
            AudioSystem.getTargetDataLine(format)
        }
    }
}
