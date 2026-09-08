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
}
