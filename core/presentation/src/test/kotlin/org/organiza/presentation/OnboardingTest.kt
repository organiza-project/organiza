package org.organiza.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingTest {
    @Test
    fun `the steps run in order and stop at done`() {
        var step = OnboardingStep.WELCOME
        val seen = mutableListOf(step)
        repeat(6) { step = Onboarding.next(step); seen += step }
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.PERMISSIONS, OnboardingStep.BACKEND, OnboardingStep.AI, OnboardingStep.DONE),
            seen.distinct(),
        )
        assertEquals(OnboardingStep.DONE, step)
    }

    @Test
    fun `back goes one step, and never before the welcome`() {
        assertEquals(OnboardingStep.PERMISSIONS, Onboarding.back(OnboardingStep.BACKEND))
        assertEquals(OnboardingStep.WELCOME, Onboarding.back(OnboardingStep.WELCOME))
    }

    @Test
    fun `every permission says why, and app blocking is the one that is required`() {
        val rows = Onboarding.permissions(emptySet())
        assertEquals(Permission.entries, rows.map { it.permission })
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
        assertEquals("Continue without blocking", Onboarding.continueLabel(OnboardingStep.PERMISSIONS, emptySet()))
        assertEquals("Continue", Onboarding.continueLabel(OnboardingStep.PERMISSIONS, setOf(Permission.ACCESSIBILITY)))
        assertEquals("Skip for now", Onboarding.continueLabel(OnboardingStep.AI, emptySet()))
        assertEquals("Start", Onboarding.continueLabel(OnboardingStep.WELCOME, emptySet()))
    }

    @Test
    fun `progress counts the steps a person sees`() {
        assertEquals("Step 1 of 4", Onboarding.progress(OnboardingStep.WELCOME))
        assertEquals("Step 4 of 4", Onboarding.progress(OnboardingStep.AI))
    }
}
