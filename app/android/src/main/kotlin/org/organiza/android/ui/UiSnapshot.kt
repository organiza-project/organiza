package org.organiza.android.ui

import androidx.compose.runtime.Immutable
import org.organiza.ai.CopilotAdvice
import org.organiza.presentation.ArenaModel
import org.organiza.presentation.ChoicesModel
import org.organiza.presentation.FocusModel
import org.organiza.presentation.HabitMatrix
import org.organiza.presentation.Permission
import org.organiza.sync.BackendKind

@Immutable
data class Upcoming(val label: String, val time: String, val length: String)

@Immutable
data class ChallengeView(val id: String, val title: String, val state: String, val model: ArenaModel)

/** The backend choice and account, for onboarding and the Vault (ADR-0015). A null kind is local only. */
@Immutable
data class AccountView(
    val kind: BackendKind? = null,
    val url: String = "",
    val signedInAs: String? = null,
    val hostedAvailable: Boolean = false,
    val busy: Boolean = false,
)

@Immutable
data class CopilotView(val advice: CopilotAdvice? = null, val ranOn: String? = null, val busy: Boolean = false)

/** Everything the screens show, built on the storage thread and swapped in whole. */
@Immutable
data class UiSnapshot(
    val onboarding: Boolean = false,
    val permissions: Set<Permission> = emptySet(),
    val focus: FocusModel? = null,
    val canStop: Boolean = false,
    val choices: ChoicesModel? = null,
    val copilot: CopilotView = CopilotView(),
    val habits: HabitMatrix? = null,
    val upcoming: List<Upcoming> = emptyList(),
    val challenges: List<ChallengeView> = emptyList(),
    val account: AccountView = AccountView(),
    val hasApiKey: Boolean = false,
    val voiceConsent: Boolean = false,
    val pendingCommand: String? = null,
    val message: String? = null,
    val storageProblem: String? = null,
    val latestInvite: String? = null,
    val blockedApps: List<String> = emptyList(),
    val blockedSites: List<String> = emptyList(),
    val stored: List<Pair<String, String>> = emptyList(),
)
