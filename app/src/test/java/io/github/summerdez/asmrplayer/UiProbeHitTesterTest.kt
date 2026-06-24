package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.ui.UiProbeBounds
import io.github.summerdez.asmrplayer.ui.UiProbeHitTester
import io.github.summerdez.asmrplayer.ui.UiProbeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiProbeHitTesterTest {
    @Test
    fun selectsSmallestContainingTarget() {
        val root = target(
            key = 1,
            order = 1L,
            id = "root",
            bounds = UiProbeBounds(left = 0f, top = 0f, right = 300f, bottom = 300f),
        )
        val row = target(
            key = 2,
            order = 2L,
            id = "row",
            bounds = UiProbeBounds(left = 20f, top = 20f, right = 220f, bottom = 90f),
        )
        val button = target(
            key = 3,
            order = 3L,
            id = "button",
            bounds = UiProbeBounds(left = 40f, top = 35f, right = 120f, bottom = 75f),
        )

        val hit = UiProbeHitTester.hit(listOf(root, row, button), x = 60f, y = 50f)

        assertEquals("button", hit.selected?.id)
        assertEquals(listOf("button", "row", "root"), hit.candidates.map { it.id })
    }

    @Test
    fun prefersNewestRegistrationForSameArea() {
        val first = target(
            key = 1,
            order = 1L,
            id = "first",
            bounds = UiProbeBounds(left = 0f, top = 0f, right = 100f, bottom = 60f),
        )
        val second = target(
            key = 2,
            order = 2L,
            id = "second",
            bounds = UiProbeBounds(left = 0f, top = 0f, right = 100f, bottom = 60f),
        )

        val hit = UiProbeHitTester.hit(listOf(first, second), x = 50f, y = 30f)

        assertEquals("second", hit.selected?.id)
        assertEquals(listOf("second", "first"), hit.candidates.map { it.id })
    }

    @Test
    fun returnsNoSelectionForEmptyPoint() {
        val hit = UiProbeHitTester.hit(
            targets = listOf(
                target(
                    key = 1,
                    order = 1L,
                    id = "row",
                    bounds = UiProbeBounds(left = 10f, top = 10f, right = 100f, bottom = 100f),
                ),
            ),
            x = 150f,
            y = 150f,
        )

        assertNull(hit.selected)
        assertEquals(emptyList<UiProbeTarget>(), hit.candidates)
    }

    private fun target(
        key: Int,
        order: Long,
        id: String,
        bounds: UiProbeBounds,
    ): UiProbeTarget {
        return UiProbeTarget(
            key = key,
            registeredOrder = order,
            id = id,
            label = id,
            sourceHint = "UiProbeHitTesterTest.kt",
            metadata = emptyMap(),
            bounds = bounds,
        )
    }
}
