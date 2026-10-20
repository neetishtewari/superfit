package com.superfit.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 9 PM reminder re-schedules itself, so it must never cancel the run that is about to show
 * the notification. It used to, with REPLACE, and the reminder never appeared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun reminders(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(ReminderScheduler.WORK_NAME).get()

    @Test
    fun `starting the app keeps the pending reminder`() {
        ReminderScheduler.scheduleReminder(context, 21, 0)
        val pending = reminders().single().id

        ReminderScheduler.scheduleReminder(context, 21, 0, ExistingWorkPolicy.KEEP)

        val after = reminders()
        assertEquals(1, after.size)
        assertEquals(pending, after.single().id)
        assertEquals(WorkInfo.State.ENQUEUED, after.single().state)
    }

    @Test
    fun `tomorrow's reminder queues behind today's instead of cancelling it`() {
        ReminderScheduler.scheduleReminder(context, 21, 0)
        val today = reminders().single().id

        ReminderScheduler.scheduleReminder(context, 21, 0, ExistingWorkPolicy.APPEND_OR_REPLACE)

        val states = reminders().associate { it.id to it.state }
        assertEquals(2, states.size)
        assertEquals(WorkInfo.State.ENQUEUED, states[today])
        assertEquals(1, states.values.count { it == WorkInfo.State.BLOCKED })
    }

    @Test
    fun `changing the time replaces the pending reminder`() {
        ReminderScheduler.scheduleReminder(context, 21, 0)
        val old = reminders().single().id

        ReminderScheduler.scheduleReminder(context, 20, 30)

        // REPLACE cancels the old request and WorkManager may drop it from the list entirely.
        val pending = reminders().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, pending.size)
        assertNotEquals(old, pending.single().id)
    }

    private fun at(hour: Int, minute: Int, second: Int = 0) = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 18, hour, minute, second)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun `delay is until today's time when it is still ahead`() {
        assertEquals(TimeUnit.HOURS.toMillis(3), ReminderScheduler.delayUntil(21, 0, at(18, 0)))
    }

    @Test
    fun `a reminder running just after its time schedules tomorrow`() {
        val delay = ReminderScheduler.delayUntil(21, 0, at(21, 0, 5))
        assertEquals(TimeUnit.HOURS.toMillis(24) - TimeUnit.SECONDS.toMillis(5), delay)
    }

    @Test
    fun `exactly on time schedules tomorrow, never zero`() {
        assertEquals(TimeUnit.HOURS.toMillis(24), ReminderScheduler.delayUntil(21, 0, at(21, 0)))
    }
}
