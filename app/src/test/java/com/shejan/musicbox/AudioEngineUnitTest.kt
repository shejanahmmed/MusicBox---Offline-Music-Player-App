package com.shejan.musicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineUnitTest {

    @Test
    fun testRepeatModeCycle() {
        var mode = MusicService.REPEAT_OFF
        mode = (mode + 1) % 3
        assertEquals(MusicService.REPEAT_ALL, mode)
        mode = (mode + 1) % 3
        assertEquals(MusicService.REPEAT_ONE, mode)
        mode = (mode + 1) % 3
        assertEquals(MusicService.REPEAT_OFF, mode)
    }

    @Test
    fun testQueueAddAndMoveLogic() {
        val list = mutableListOf(
            Track(1L, "Track 1", "Artist 1", "uri1", "Album 1"),
            Track(2L, "Track 2", "Artist 2", "uri2", "Album 2"),
            Track(3L, "Track 3", "Artist 3", "uri3", "Album 3")
        )

        // Move item from 0 to 2
        val item = list.removeAt(0)
        list.add(2, item)

        assertEquals("Track 2", list[0].title)
        assertEquals("Track 3", list[1].title)
        assertEquals("Track 1", list[2].title)
    }

    @Test
    fun testPlaybackParameterCoercion() {
        val speedTooLow = 0.1f
        val speedClamped = speedTooLow.coerceIn(0.25f, 3.0f)
        assertEquals(0.25f, speedClamped, 0.001f)

        val speedTooHigh = 5.0f
        val speedHighClamped = speedTooHigh.coerceIn(0.25f, 3.0f)
        assertEquals(3.0f, speedHighClamped, 0.001f)

        val pitchTooLow = 0.05f
        val pitchClamped = pitchTooLow.coerceIn(0.25f, 2.0f)
        assertEquals(0.25f, pitchClamped, 0.001f)
    }

    @Test
    fun testVolumeFadeInterpolationAndClamping() {
        val targetVolume = 1.0f
        val startVol = 0.0f
        val durationMs = 180L

        // Test elapsed 0ms
        val elapsed0 = 0L
        val fraction0 = (elapsed0.toFloat() / durationMs).coerceIn(0.0f, 1.0f)
        val interpolated0 = 1.0f - (1.0f - fraction0) * (1.0f - fraction0)
        val vol0 = startVol + (targetVolume - startVol) * interpolated0
        assertEquals(0.0f, vol0, 0.001f)

        // Test elapsed half-way (90ms)
        val elapsedHalf = 90L
        val fractionHalf = (elapsedHalf.toFloat() / durationMs).coerceIn(0.0f, 1.0f)
        val interpolatedHalf = 1.0f - (1.0f - fractionHalf) * (1.0f - fractionHalf)
        val volHalf = startVol + (targetVolume - startVol) * interpolatedHalf
        assertTrue("Decelerate curve should be > 0.5 at midpoint", volHalf > 0.5f)
        assertTrue("Volume should remain <= 1.0f", volHalf <= 1.0f)

        // Test elapsed >= duration (180ms)
        val elapsedFull = 200L
        val fractionFull = (elapsedFull.toFloat() / durationMs).coerceIn(0.0f, 1.0f)
        val interpolatedFull = 1.0f - (1.0f - fractionFull) * (1.0f - fractionFull)
        val volFull = (startVol + (targetVolume - startVol) * interpolatedFull).coerceIn(0.0f, 1.0f)
        assertEquals(1.0f, volFull, 0.001f)

        // Test volume bounds clamping
        val clampedNegative = (-0.5f).coerceIn(0.0f, 1.0f)
        assertEquals(0.0f, clampedNegative, 0.001f)

        val clampedOver = 1.5f.coerceIn(0.0f, 1.0f)
        assertEquals(1.0f, clampedOver, 0.001f)
    }

    @Test
    fun testDelayedAudioFocusFlag() {
        var resumeOnFocusGain = false

        // Simulate AUDIOFOCUS_REQUEST_DELAYED
        fun onDelayedFocus() {
            resumeOnFocusGain = true
        }

        onDelayedFocus()
        assertTrue("resumeOnFocusGain should be set to true on delayed focus", resumeOnFocusGain)

        // Simulate subsequent AUDIOFOCUS_GAIN
        var didResume = false
        fun onFocusGain() {
            if (resumeOnFocusGain) {
                resumeOnFocusGain = false
                didResume = true
            }
        }

        onFocusGain()
        assertTrue("Player should resume when focus is finally gained", didResume)
        assertFalse("resumeOnFocusGain should be cleared after consumption", resumeOnFocusGain)
    }
}
