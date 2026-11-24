package org.organiza.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingTest {
    @Test
    fun `the steps run in order and stop at done`() {
        var step = OnboardingStep.WELCOME
        val seen = mutableListOf(step)
        repeat(8) { step = Onboarding.next(step); seen += step }
        assertEquals(
            listOf(
                OnboardingStep.WELCOME, OnboardingStep.BLOCKING, OnboardingStep.FURTHER, OnboardingStep.LOCKOUT,
                OnboardingStep.DEVICES, OnboardingStep.AI, OnboardingStep.DONE,
            ),
            seen.distinct(),
        )
        assertEquals(OnboardingStep.DONE, step)
    }

    @Test
    fun `back goes one step, and never before the welcome`() {
        assertEquals(OnboardingStep.LOCKOUT, Onboarding.back(OnboardingStep.DEVICES))
        assertEquals(OnboardingStep.WELCOME, Onboarding.back(OnboardingStep.WELCOME))
    }

    @Test
    fun `every permission says why, and app blocking is the one that is required`() {
        val rows = Onboarding.permissions(emptySet())
        assertEquals(Device.PHONE.permissions, rows.map { it.permission })
        assertEquals(listOf(Permission.ACCESSIBILITY), rows.filter { it.required }.map { it.permission })
        Permission.entries.forEach {
            assertTrue(it.why.isNotBlank())
            assertTrue(it.fallback.isNotBlank())
        }
    }

    @Test
    fun `the pause screen comes with app blocking and needs no grant of its own`() {
        assertEquals(false, Onboarding.permissions(emptySet()).single { it.permission == Permission.OVERLAY }.granted)
        val granted = Onboarding.permissions(setOf(Permission.ACCESSIBILITY))
        assertEquals(true, granted.single { it.permission == Permission.OVERLAY }.granted)
        assertEquals(false, granted.single { it.permission == Permission.OVERLAY }.grantable)
    }

    @Test
    fun `continuing without app blocking is allowed but named`() {
        assertEquals("Continue without blocking", Onboarding.continueLabel(OnboardingStep.BLOCKING, emptySet()))
        assertEquals("Continue", Onboarding.continueLabel(OnboardingStep.BLOCKING, setOf(Permission.ACCESSIBILITY)))
        assertEquals("Skip for now", Onboarding.continueLabel(OnboardingStep.AI, emptySet()))
        assertEquals("Start", Onboarding.continueLabel(OnboardingStep.WELCOME, emptySet()))
    }

    @Test
    fun `the desktop asks for tracking, starting at login, the tray and a shortcut, and tracking is required`() {
        val rows = Onboarding.permissions(emptySet(), Device.DESKTOP)
        assertEquals(listOf(Permission.TRACKING, Permission.AUTOSTART, Permission.TRAY, Permission.SHORTCUT), rows.map { it.permission })
        assertEquals(listOf(Permission.TRACKING), rows.filter { it.required }.map { it.permission })
        assertTrue(rows.all { it.grantable && !it.granted })
        assertEquals("Continue without blocking", Onboarding.continueLabel(OnboardingStep.BLOCKING, emptySet(), Device.DESKTOP))
        assertEquals("Continue", Onboarding.continueLabel(OnboardingStep.BLOCKING, setOf(Permission.TRACKING), Device.DESKTOP))
        // A phone permission never turns up on the desktop, and the other way round.
        assertTrue(Device.PHONE.permissions.none { it in Device.DESKTOP.permissions })
    }

    @Test
    fun `progress counts the steps a person sees`() {
        assertEquals("Step 1 of 6", Onboarding.progress(OnboardingStep.WELCOME))
        assertEquals("Step 6 of 6", Onboarding.progress(OnboardingStep.AI))
    }

    @Test
    fun `permissions come a few at a time, blocking first, and every one is asked once`() {
        Device.entries.forEach { device ->
            val blocking = Onboarding.permissions(emptySet(), device, OnboardingStep.BLOCKING).map { it.permission }
            val further = Onboarding.permissions(emptySet(), device, OnboardingStep.FURTHER).map { it.permission }
            assertEquals(device.required, blocking.first())
            assertTrue(blocking.size <= 3 && further.size <= 3)
            assertEquals(device.permissions.toSet(), (blocking + further).toSet())
            assertTrue(blocking.none { it in further })
        }
        assertTrue(Onboarding.permissions(emptySet(), Device.PHONE, OnboardingStep.LOCKOUT).isEmpty())
    }

    @Test
    fun `sharing usage takes three steps, each done in turn, with the next one marked`() {
        val none = DeviceSetup.steps(syncOn = false, signedIn = false, linked = false)
        assertEquals(3, none.size)
        assertEquals(listOf(false, false, false), none.map { it.done })
        assertEquals(0, none.indexOfFirst { it.current })
        val signedIn = DeviceSetup.steps(syncOn = true, signedIn = true, linked = false)
        assertEquals(listOf(true, true, false), signedIn.map { it.done })
        assertEquals(2, signedIn.indexOfFirst { it.current })
        assertTrue(DeviceSetup.steps(syncOn = true, signedIn = true, linked = true).none { it.current })
    }
}
