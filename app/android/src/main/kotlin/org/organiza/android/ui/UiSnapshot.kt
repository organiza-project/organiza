package org.organiza.android.ui

import androidx.compose.runtime.Immutable
import org.organiza.ai.CopilotAdvice
import org.organiza.ai.SuggestedBlock
import org.organiza.presentation.AgendaModel
import org.organiza.presentation.ArenaModel
import org.organiza.presentation.FocusWeek
import org.organiza.presentation.HomeModel
import org.organiza.presentation.ProgressionModel
import org.organiza.presentation.ChoicesModel
import org.organiza.presentation.FocusModel
import org.organiza.presentation.HabitMatrix
import org.organiza.presentation.Permission
import org.organiza.sync.BackendKind

@Immutable
data class Upcoming(val label: String, val time: String, val length: String)

@Immutable
data class ChallengeView(val id: String, val title: String, val state: String, val model: ArenaModel)

/** The backend choice and account, for onboarding and Profile (ADR-0015). A null kind is local only. */
@Immutable
data class AccountView(
    val kind: BackendKind? = null,
    val url: String = "",
    val signedInAs: String? = null,
    val hostedAvailable: Boolean = false,
    val busy: Boolean = false,
)

/** One app on the distraction hub: on the focus list or not, today's use and its daily limit. */
@Immutable
data class HubApp(
    val pkg: String,
    val label: String,
    val onList: Boolean,
    val usedMinutes: Long,
    val limitMinutes: Long?,
    val locked: Boolean,
)

@Immutable
data class HubView(
    val apps: List<HubApp> = emptyList(),
    val sites: List<String> = emptyList(),
    val blockReels: Boolean = false,
    val blockShorts: Boolean = false,
    val usageAllowed: Boolean = false,
)

/** The schedule import in progress: text read from a photo, a dictation or the keyboard, then blocks. */
@Immutable
data class ImportView(
    val source: String = "",
    val blocks: List<SuggestedBlock> = emptyList(),
    val busy: String? = null,
    val status: String? = null,
)

@Immutable
data class CopilotView(val advice: CopilotAdvice? = null, val ranOn: String? = null, val busy: Boolean = false)

/** Everything the screens show, built on the storage thread and swapped in whole. */
@Immutable
data class UiSnapshot(
    val onboarding: Boolean = false,
    val permissions: Set<Permission> = emptySet(),
    val focus: FocusModel? = null,
    val home: HomeModel? = null,
    val agendaToday: AgendaModel? = null,
    val agendaTomorrow: AgendaModel? = null,
    val progression: ProgressionModel? = null,
    val week: FocusWeek? = null,
    val hub: HubView = HubView(),
    val schedule: ImportView = ImportView(),
    val nudges: List<String> = emptyList(),
    val selfName: String = "You",
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
