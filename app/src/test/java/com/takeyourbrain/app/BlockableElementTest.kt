package com.takeyourbrain.app

import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.data.db.ScheduleMode
import com.takeyourbrain.app.domain.BlockableElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Блокировка частей приложения (YouTube Shorts): хранение в правиле и поиск по пакету. */
class BlockableElementTest {

    private fun rule(elements: String) = AppRule(
        packageName = "com.google.android.youtube",
        fullBlock = false,
        weekdayLimitMinutes = null,
        scheduleMode = ScheduleMode.NONE,
        blockedElements = elements,
    )

    @Test
    fun elementsAreParsed() {
        assertEquals(emptySet<String>(), rule("").elements)
        assertEquals(setOf("youtube_shorts"), rule("youtube_shorts").elements)
        assertEquals(setOf("a", "b"), rule("a,b").elements)
    }

    @Test
    fun elementLimitsAreParsed() {
        // "youtube_shorts" — всегда, "youtube_shorts:15" — после 15 минут в день
        assertEquals(mapOf("youtube_shorts" to null), rule("youtube_shorts").elementLimits)
        assertEquals(mapOf("youtube_shorts" to 15), rule("youtube_shorts:15").elementLimits)
        assertEquals(setOf("youtube_shorts"), rule("youtube_shorts:15").elements)
    }

    @Test
    fun ruleWithOnlyShortsStillHasCondition() {
        assertTrue(rule("youtube_shorts").hasOtherConditions)
        assertFalse(rule("").hasOtherConditions)
    }

    @Test
    fun shortsBelongToYouTube() {
        assertEquals(listOf(BlockableElement.YOUTUBE_SHORTS), BlockableElement.forPackage("com.google.android.youtube"))
        assertTrue(BlockableElement.forPackage("com.zhiliaoapp.musically").isEmpty())
        assertEquals(BlockableElement.YOUTUBE_SHORTS, BlockableElement.byId("youtube_shorts"))
    }
}
