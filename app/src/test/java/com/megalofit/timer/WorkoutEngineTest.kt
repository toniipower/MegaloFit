package com.megalofit.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEngineTest {
    private var now = 0L
    private val engine = WorkoutEngine { now }

    private val steps = listOf(
        WorkoutStep(PhaseType.WORK, "Plancha", 1, 2, 0, durationSec = 30),
        WorkoutStep(PhaseType.REST, "Descanso", 1, 2, 0, durationSec = 60, nextLabel = "x"),
        WorkoutStep(PhaseType.WORK, "Flexiones", 1, 1, 10, durationSec = 0),
        WorkoutStep(PhaseType.REST, "Descanso", 1, 1, 0, durationSec = 20),
    )

    @Test
    fun countsDownAgainstTheClock() {
        engine.start(1, "R", steps)
        now = 10_000
        assertFalse(engine.tick())
        assertEquals(20_000, engine.state.value.remainingMs)
    }

    @Test
    fun catchesUpAfterLongSleepAndStopsAtManualStep() {
        engine.start(1, "R", steps)
        now = 200_000 // 30 s + 60 s ya pasados de sobra
        assertTrue(engine.tick())
        assertEquals(2, engine.state.value.index)
        assertEquals(0, engine.state.value.remainingMs)
    }

    @Test
    fun carriesOverflowIntoNextTimedStep() {
        engine.start(1, "R", steps)
        now = 35_000 // 5 s dentro del descanso
        assertTrue(engine.tick())
        assertEquals(1, engine.state.value.index)
        assertEquals(55_000, engine.state.value.remainingMs)
    }

    @Test
    fun pauseFreezesTimeAndElapsed() {
        engine.start(1, "R", steps)
        now = 5_000; engine.tick()
        engine.pause()
        now = 50_000; engine.tick()
        assertEquals(25_000, engine.state.value.remainingMs)
        engine.resume()
        now = 55_000; engine.tick()
        assertEquals(20_000, engine.state.value.remainingMs)
        assertEquals(10_000, engine.state.value.elapsedMs)
    }

    @Test
    fun manualNextThenFinishes() {
        engine.start(1, "R", steps)
        engine.next(); engine.next() // paso manual
        engine.next() // último descanso
        now = 21_000
        assertTrue(engine.tick())
        assertTrue(engine.state.value.finished)
    }
}
