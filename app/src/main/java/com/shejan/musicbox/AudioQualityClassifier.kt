/*
 * Copyright (C) 2026 Shejan
 *
 * This file is part of MusicBox.
 *
 * MusicBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MusicBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MusicBox.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.shejan.musicbox

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

data class AudioQualityInfo(
    val title: String,
    val badgeText: String,
    val description: String,
    val format: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val bitDepth: Int,
    val isLossless: Boolean
)

object AudioQualityClassifier {

    fun classify(uri: String, isVideo: Boolean = false): AudioQualityInfo {
        var format = "AUDIO"
        var bitrateBps = 0L
        var sampleRate = 44100
        var channelCount = 2
        var bitDepth = 16
        var isLossless = false

        // 1. MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(uri)
                val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                if (br != null) {
                    bitrateBps = br.toLongOrNull() ?: 0L
                }
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                if (mime != null) {
                    val sub = mime.substringAfter("/")
                    format = when (sub.lowercase(Locale.ROOT)) {
                        "mpeg", "mp3", "mpeg3", "x-mpeg-3" -> "MP3"
                        "flac", "x-flac" -> { isLossless = true; "FLAC" }
                        "wav", "x-wav", "wave" -> { isLossless = true; "WAV" }
                        "mp4", "m4a" -> "M4A"
                        "aac", "x-aac" -> "AAC"
                        "ogg", "vorbis" -> "OGG"
                        "opus" -> "OPUS"
                        "x-matroska" -> "MKA"
                        "alac" -> { isLossless = true; "ALAC" }
                        else -> sub.substringBefore(";").trim().uppercase(Locale.ROOT)
                    }
                }
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {}

        // 2. MediaExtractor for deeper hardware audio format fields
        try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(uri)
                for (i in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (trackFormat.containsKey(MediaFormat.KEY_BIT_RATE) && bitrateBps == 0L) {
                            bitrateBps = trackFormat.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                        }
                        if (mime.contains("flac") || mime.contains("raw") || mime.contains("wav")) {
                            isLossless = true
                        }
                        break
                    }
                }
            } finally {
                extractor.release()
            }
        } catch (_: Exception) {}

        // 3. Fallback / Header parser for Lossless Bit Depth (FLAC & WAV) & Extension Check
        val file = File(uri)
        if (file.exists() && file.isFile) {
            val ext = file.extension.uppercase(Locale.ROOT)
            if (ext.isNotEmpty()) {
                // If format is generic or audio, or extension is a standard audio container, prioritize accurate extension
                if (format == "AUDIO" || format == "OCTET-STREAM" || format.startsWith("X-") || ext in listOf("MP3", "FLAC", "WAV", "M4A", "AAC", "OGG", "OPUS", "WMA", "ALAC", "AIFF", "DSF", "DFF", "MKA")) {
                    format = ext
                }
            }

            if (ext == "FLAC") {
                isLossless = true
                format = "FLAC"
                val flacInfo = readFlacStreamInfo(file)
                if (flacInfo != null) {
                    if (flacInfo.sampleRate > 0) sampleRate = flacInfo.sampleRate
                    if (flacInfo.bitDepth > 0) bitDepth = flacInfo.bitDepth
                }
            } else if (ext == "WAV") {
                isLossless = true
                format = "WAV"
                val wavInfo = readWavHeader(file)
                if (wavInfo != null) {
                    if (wavInfo.sampleRate > 0) sampleRate = wavInfo.sampleRate
                    if (wavInfo.bitDepth > 0) bitDepth = wavInfo.bitDepth
                }
            }
        }

        val bitrateKbps = if (bitrateBps > 0) (bitrateBps / 1000).toInt() else {
            // Estimated bitrate from duration & file length if missing
            if (file.exists() && file.length() > 0 && isLossless) {
                // Calculate uncompressed / stream bitrate
                (file.length() * 8 / 1000 / 240).toInt().coerceIn(600, 9216)
            } else {
                320
            }
        }

        // Format sample rate for badge (e.g. 192000 -> "192k", 44100 -> "44.1k", 96000 -> "96k")
        val sampleRateK = formatSampleRate(sampleRate)

        // Classify into screenshot-style tiers
        return when {
            isLossless && (bitDepth >= 24 || sampleRate >= 96000) -> {
                AudioQualityInfo(
                    title = "Max Quality",
                    badgeText = "${bitDepth}-BIT / $sampleRateK",
                    description = "Up to ${bitDepth}-bit / $sampleRateK kHz • Lossless Studio $format",
                    format = format,
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = sampleRate,
                    bitDepth = bitDepth,
                    isLossless = true
                )
            }
            isLossless && (bitDepth >= 24 || sampleRate > 48000) -> {
                AudioQualityInfo(
                    title = "Hi-Res Audio",
                    badgeText = "${bitDepth}-BIT / $sampleRateK",
                    description = "${bitDepth}-bit / $sampleRateK kHz • Lossless Studio $format",
                    format = format,
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = sampleRate,
                    bitDepth = bitDepth,
                    isLossless = true
                )
            }
            isLossless -> {
                AudioQualityInfo(
                    title = "CD Lossless",
                    badgeText = "${bitDepth}-BIT / $sampleRateK",
                    description = "${bitDepth}-bit / $sampleRateK kHz • Lossless CD $format",
                    format = format,
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = sampleRate,
                    bitDepth = bitDepth,
                    isLossless = true
                )
            }
            bitrateKbps >= 300 -> {
                AudioQualityInfo(
                    title = "High Quality",
                    badgeText = "$bitrateKbps kbps",
                    description = "$bitrateKbps kbps • $format (High Fidelity)",
                    format = format,
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = sampleRate,
                    bitDepth = 16,
                    isLossless = false
                )
            }
            bitrateKbps in 190..299 -> {
                AudioQualityInfo(
                    title = "Standard Quality",
                    badgeText = "$bitrateKbps kbps",
                    description = "$bitrateKbps kbps • $format",
                    format = format,
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = sampleRate,
                    bitDepth = 16,
                    isLossless = false
                )
            }
            else -> {
                val displayKbps = if (bitrateKbps > 0) "$bitrateKbps kbps" else "Standard"
                AudioQualityInfo(
                    title = "Standard Quality",
                    badgeText = displayKbps,
                    description = "$displayKbps • $format (Data Saver)",
                    format = format,
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = sampleRate,
                    bitDepth = 16,
                    isLossless = false
                )
            }
        }
    }

    private fun formatSampleRate(sampleRateHz: Int): String {
        return when (sampleRateHz) {
            192000 -> "192k"
            96000 -> "96k"
            88200 -> "88.2k"
            48000 -> "48k"
            44100 -> "44.1k"
            32000 -> "32k"
            else -> {
                if (sampleRateHz >= 1000) {
                    val k = sampleRateHz / 1000.0
                    if (k == k.toLong().toDouble()) "${k.toLong()}k" else String.format(Locale.ROOT, "%.1fk", k)
                } else {
                    "${sampleRateHz}Hz"
                }
            }
        }
    }

    private data class LosslessHeaderInfo(val sampleRate: Int, val bitDepth: Int)

    private fun readFlacStreamInfo(file: File): LosslessHeaderInfo? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(42)
                val read = fis.read(header)
                if (read >= 42 && header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() && header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()) {
                    // FLAC STREAMINFO metadata starts at offset 8 (after 4-byte fLaC + 4-byte block header)
                    // Offset 18-20: sample rate (20 bits), channels (3 bits), bits per sample (5 bits)
                    val b18 = header[18].toInt() and 0xFF
                    val b19 = header[19].toInt() and 0xFF
                    val b20 = header[20].toInt() and 0xFF
                    val b21 = header[21].toInt() and 0xFF

                    val sampleRate = (b18 shl 12) or (b19 shl 4) or (b20 ushr 4)
                    val bitsPerSample = (((b20 and 0x01) shl 4) or (b21 ushr 4)) + 1

                    return LosslessHeaderInfo(sampleRate, bitsPerSample)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun readWavHeader(file: File): LosslessHeaderInfo? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(44)
                val read = fis.read(header)
                if (read >= 44 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()) {
                    val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    val sampleRate = buffer.getInt(24)
                    val bitsPerSample = buffer.getShort(34).toInt()
                    return LosslessHeaderInfo(sampleRate, bitsPerSample)
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
