package org.organiza.android.ui

import androidx.compose.runtime.Immutable
import org.organiza.presentation.ArenaModel
import org.organiza.presentation.FocusModel
import org.organiza.presentation.HabitMatrix

@Immutable
data class Upcoming(val label: String, val time: String, val length: String)

@Immutable
data class ChallengeView(val id: String, val title: String, val state: String, val model: ArenaModel)

/** Everything the screens show, built on the storage thread and swapped in whole. */
@Immutable
data class UiSnapshot(
    val focus: FocusModel? = null,
    val canStop: Boolean = false,
    val habits: HabitMatrix? = null,
    val upcoming: List<Upcoming> = emptyList(),
    val challenges: List<ChallengeView> = emptyList(),
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
