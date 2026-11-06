package com.takeyourbrain.app

import com.takeyourbrain.app.data.db.daysFromMask
import com.takeyourbrain.app.data.db.daysToMask
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/** Проверяем, что дни недели правильно превращаются в число для базы и обратно. */
class DaysMaskTest {

    @Test
    fun mondayIsFirstBit() {
        assertEquals(1, daysToMask(setOf(DayOfWeek.MONDAY)))
    }

    @Test
    fun allDaysAreSevenBits() {
        assertEquals(0b1111111, daysToMask(DayOfWeek.entries.toSet()))
    }

    @Test
    fun roundTrip() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)
        assertEquals(days, daysFromMask(daysToMask(days)))
    }
}
