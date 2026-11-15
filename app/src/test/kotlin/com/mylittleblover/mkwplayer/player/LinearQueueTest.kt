package com.mylittleblover.mkwplayer.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinearQueueTest {

    private fun entry(id: String): LinearQueue.Entry = LinearQueue.Entry(
        key = "k:$id",
        title = "Track $id",
        subtitle = "1990 · Super Mario World",
    )

    @Test
    fun freezesBaseListAndUsesLifoForPlayNext() {
        val queue = LinearQueue()
        val list = listOf(entry("A"), entry("B"), entry("C"))
        assertEquals("k:A", queue.startSession(list, 0)?.key)

        // LIFO insertion (#63, #64): enqueue B2 then B1 -> B1 plays first, then B2, then frozen base B -> C.
        queue.enqueueNext(entry("N1"))
        queue.enqueueNext(entry("N2"))

        val snap = queue.snapshot()
        assertEquals(listOf("k:N2", "k:N1"), snap.tempQueue.map { it.key })
        assertEquals(listOf("k:B", "k:C"), snap.upNext.map { it.key })

        assertEquals("k:N2", (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key)
        assertEquals("k:N1", (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key)
        assertEquals("k:B", (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key)
        assertEquals("k:C", (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key)
    }

    @Test
    fun supportsRealPlaybackHistoryForPreviousAndForwardHistoryPriority() {
        val queue = LinearQueue(Random(42))
        queue.startSession(listOf(entry("A"), entry("B"), entry("C")), 0)
        queue.enqueueNext(entry("TEMP"))

        // Play order: A -> TEMP -> B
        assertEquals("k:TEMP", (queue.next() as LinearQueue.StepResult.Play).entry.key)
        assertEquals("k:B", (queue.next() as LinearQueue.StepResult.Play).entry.key)

        // Past threshold restarts current instead of stepping backward.
        assertEquals(
            LinearQueue.StepResult.RestartCurrent,
            queue.previous(restartThresholdMs = 3_000L, elapsedMs = 4_000L),
        )

        // Within threshold steps strictly backward along real history: B -> TEMP -> A (#58).
        assertEquals("k:TEMP", (queue.previous(restartThresholdMs = 3_000L, elapsedMs = 500L) as LinearQueue.StepResult.Play).entry.key)
        assertEquals("k:A", (queue.previous() as LinearQueue.StepResult.Play).entry.key)
        assertEquals(LinearQueue.StepResult.RestartCurrent, queue.previous())

        // Next prioritizes forward history back out: A -> TEMP -> B (#59).
        assertEquals("k:TEMP", (queue.next() as LinearQueue.StepResult.Play).entry.key)
        assertEquals("k:B", (queue.next() as LinearQueue.StepResult.Play).entry.key)
        assertEquals("k:C", (queue.next() as LinearQueue.StepResult.Play).entry.key)
    }

    @Test
    fun shufflesEachRoundWithoutRepeatsAndAvoidsBoundaryImmediateRepeat() {
        val queue = LinearQueue(Random(7))
        val items = listOf(entry("1"), entry("2"), entry("3"), entry("4"))
        val first = queue.startSession(items, startIndex = 0, shuffleOverride = true)!!
        assertEquals("k:1", first.key)

        val round1 = mutableListOf(first.key)
        repeat(3) {
            round1 += (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key
        }
        assertEquals(items.map { it.key }.toSet(), round1.toSet())
        assertEquals(4, round1.size)

        // First track of round 2 must not equal the final track of round 1 (#55, #57).
        val round2First = (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key
        assertFalse(round2First == round1.last())

        val round2 = mutableListOf(round2First)
        repeat(3) {
            round2 += (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key
        }
        assertEquals(items.map { it.key }.toSet(), round2.toSet())
    }

    @Test
    fun singleRepeatLoopsNaturallyButAdvancesOnManualNextWithoutLosingMode() {
        val queue = LinearQueue()
        queue.startSession(listOf(entry("A"), entry("B"), entry("C")), 0)
        queue.enqueueNext(entry("TEMP"))

        assertEquals(LinearQueue.RepeatMode.SINGLE, queue.cycleRepeatMode())

        // Natural end obeys SINGLE loop ahead of temporary Play-Next queue (#65).
        assertEquals(LinearQueue.StepResult.RestartCurrent, queue.onNaturalEnd())

        // Manual Next advances into TEMP and preserves SINGLE repeat (#66).
        assertEquals("k:TEMP", (queue.next() as LinearQueue.StepResult.Play).entry.key)
        assertEquals(LinearQueue.RepeatMode.SINGLE, queue.repeatMode)
        assertEquals(LinearQueue.StepResult.RestartCurrent, queue.onNaturalEnd())

        // Cycling to OFF stops naturally once remaining tracks finish (#60).
        assertEquals(LinearQueue.RepeatMode.OFF, queue.cycleRepeatMode())
        assertEquals("k:B", (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key)
        assertEquals("k:C", (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key)
        assertEquals(LinearQueue.StepResult.Ended, queue.onNaturalEnd())
    }

    @Test
    fun queueRemovalJumpingAndMidSessionShuffleToggleWorkCleanly() {
        val queue = LinearQueue(Random(123))
        queue.startSession(listOf(entry("A"), entry("B"), entry("C"), entry("D")), 0)
        queue.enqueueNext(entry("T1"))
        queue.enqueueNext(entry("T2"))

        // T2 is at index 0, T1 at index 1. Removing T2 leaves [T1].
        assertTrue(queue.removeTempAt(0))
        assertEquals(listOf("k:T1"), queue.snapshot().tempQueue.map { it.key })

        // Jumping directly to upcoming item D skips B and C.
        val jumped = queue.jumpToUpcoming(2)
        assertEquals("k:D", jumped?.key)
        assertTrue(queue.snapshot().upNext.isEmpty())

        // Toggling shuffle OFF after starting shuffled rebuilds sequential tail cleanly.
        queue.startSession(listOf(entry("A"), entry("B"), entry("C"), entry("D")), 1, shuffleOverride = true)
        queue.setShuffle(false)
        assertEquals(listOf("k:C", "k:D"), queue.snapshot().upNext.map { it.key })
    }

    @Test
    fun roadCandidateVariantsRedrawnEachLoopWhileFixedOverridesPersist() {
        val queue = LinearQueue(Random(17))
        val stop0 = entry("Stop0")
        val stop1 = entry("Stop1")
        val candA = entry("RoadCandA").copy(sourceTrackKey = "t1")
        val candB = entry("RoadCandB").copy(sourceTrackKey = "t2")
        val candC = entry("RoadCandC").copy(sourceTrackKey = "t3")
        val autoEdge = candA.copy(roadCandidateVariants = listOf(candA, candB, candC))
        val pinnedEdge = entry("RoadPinned").copy(sourceTrackKey = "t9")

        // Route order: Stop0 -> autoEdge -> Stop1 -> pinnedEdge; no fabricated return leg (#69, #80, #81).
        queue.setRepeatMode(LinearQueue.RepeatMode.LIST)
        val first = queue.startSession(listOf(stop0, autoEdge, stop1, pinnedEdge), 0)!!
        assertEquals("k:Stop0", first.key)

        val drawnKeys = mutableSetOf<String>()
        repeat(6) {
            val drawnAuto = (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry
            assertTrue(drawnAuto.key in setOf("k:RoadCandA", "k:RoadCandB", "k:RoadCandC"))
            drawnKeys += drawnAuto.key

            assertEquals("k:Stop1", (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry.key)
            val pinned = (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry
            assertEquals("k:RoadPinned", pinned.key)

            val wrappedStart = (queue.onNaturalEnd() as LinearQueue.StepResult.Play).entry
            assertEquals("k:Stop0", wrappedStart.key)
        }
        assertTrue(drawnKeys.size >= 2, "Auto candidate pool should re-draw across loop rounds (#81), saw=$drawnKeys")
    }
}
