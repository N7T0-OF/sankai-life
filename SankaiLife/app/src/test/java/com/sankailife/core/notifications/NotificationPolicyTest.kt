package com.sankailife.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class NotificationPolicyTest {
    private fun input(
        master: Boolean = true,
        category: Boolean = true,
        pauseUntil: Long = 0,
        day: Long = 100,
        weekend: Boolean = false,
        dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        quiet: QuietHours = QuietHours.DESACTIVE,
        minute: Int = 12 * 60
    ) = NotificationPolicyInput(
        master, category, quiet, minute, day, pauseUntil, weekend, dayOfWeek
    )

    @Test fun `master and category are both required`() {
        assertFalse(notificationAllowed(input(master = false)))
        assertFalse(notificationAllowed(input(category = false)))
        assertTrue(notificationAllowed(input()))
    }

    @Test fun `pause is inclusive and then expires`() {
        assertFalse(notificationAllowed(input(pauseUntil = 100)))
        assertTrue(notificationAllowed(input(pauseUntil = 99)))
    }

    @Test fun `quiet weekend suppresses Saturday only when enabled`() {
        assertFalse(notificationAllowed(input(weekend = true, dayOfWeek = DayOfWeek.SATURDAY)))
        assertTrue(notificationAllowed(input(weekend = false, dayOfWeek = DayOfWeek.SATURDAY)))
    }

    @Test fun `quiet hours cross midnight`() {
        val night = QuietHours(enabled = true, startMinute = 21 * 60, endMinute = 9 * 60)
        assertFalse(notificationAllowed(input(quiet = night, minute = 22 * 60)))
        assertFalse(notificationAllowed(input(quiet = night, minute = 8 * 60)))
        assertTrue(notificationAllowed(input(quiet = night, minute = 12 * 60)))
    }
}
